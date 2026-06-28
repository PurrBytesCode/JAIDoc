package com.purrbyte.ai.util;

import com.purrbyte.ai.model.dto.Progress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads Spring Boot artifacts from Maven Central so that arbitrary versions can be ingested.
 *
 * <p>Unlike the JDK pipeline (which uses Adoptium's API), Spring Boot publishes its jars on Maven Central.
 * This component queries Maven's metadata API to resolve available versions, constructs the download URL
 * from the standard Maven Central layout, and caches downloaded artifacts locally.
 *
 * <p>Three artifact types are supported:
 * <ul>
 *   <li>{@code jar} — the main Spring Boot jar (contains {@code META-INF/spring-configuration-metadata.json})</li>
 *   <li>{@code sources} — the source jar used by the JsonDoclet to generate API documentation</li>
 * </ul>
 *
 * <p>Downloads are cached: an already-present file is reused. Progress is reported via a
 * {@link Consumer} of {@link Progress}. Downloads run asynchronously on virtual threads.
 */
@Slf4j
@Component
public class SpringBootArtifactDownloader {

    private static final String MAVEN_METADATA_URL = "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml";
    private static final String MAVEN_REPO_BASE = "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot";
    private static final int DOWNLOAD_BUFFER = 65536;
    private static final Pattern LATEST_TAG = Pattern.compile("<latest>([^<]+)</latest>");

    private final Executor downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final String downloadDirectory;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public SpringBootArtifactDownloader(
            @Value("${spring.boot.artifacts.download.directory}") String downloadDirectory,
            RestClient restClient,
            JsonMapper jsonMapper) {
        this.downloadDirectory = downloadDirectory;
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Resolves the latest stable Spring Boot version by querying Maven Central's metadata XML.
     * Falls back to the Solr search API if the metadata is blank or does not contain a {@code <latest>} tag.
     *
     * @return the latest release version string, or empty if the version cannot be resolved
     */
    public Optional<String> resolveLatestVersion() {
        String xml = restClient.get().uri(URI.create(MAVEN_METADATA_URL)).retrieve().body(String.class);
        if (xml != null && !xml.isBlank()) {
            Matcher matcher = LATEST_TAG.matcher(xml);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return resolveLatestFromSearchApi();
    }

    /**
     * Resolves the latest version using Maven Central's Solr search API as a fallback.
     *
     * @return the latest version string, or empty if the search fails or returns no results
     */
    private Optional<String> resolveLatestFromSearchApi() {
        String url = "https://search.maven.org/solrsearch/select?q=g:org.springframework.boot+AND+a:spring-boot&rows=1&wt=json&core=gav";
        String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
        if (body == null) {
            return Optional.empty();
        }
        JsonNode root = jsonMapper.readTree(body);
        JsonNode response = root.path("response");
        JsonNode docs = response.path("docs");
        if (docs.isArray() && !docs.isEmpty()) {
            String version = docs.get(0).path("v").asString(null);
            return Optional.ofNullable(version);
        }
        return Optional.empty();
    }

    /**
     * Downloads a Spring Boot artifact for the specified version.
     *
     * @param version          Spring Boot version (e.g. {@code "3.4.2"})
     * @param artifactType     type of artifact: {@code "jar"} or {@code "sources"}
     * @param progressCallback callback reporting download progress (maybe null)
     * @return future with the path to the downloaded file (cached on later calls)
     */
    public CompletableFuture<Path> downloadArtifact(String version, String artifactType, Consumer<Progress> progressCallback) {
        if (version == null || version.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Spring Boot version cannot be null or blank"));
        }
        String suffix;
        switch (artifactType) {
            case "jar" -> suffix = "";
            case "sources" -> suffix = "-sources";
            default -> {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown artifact type: " + artifactType + ". Supported: jar, sources"));
            }
        }
        String fileName = "spring-boot-" + version + suffix + ".jar";
        Path path = Path.of(downloadDirectory);
        Path targetFile = path.resolve(fileName);
        if (Files.exists(targetFile)) {
            log.info("Spring Boot artifact already downloaded at {} (type={})", targetFile, artifactType);
            return CompletableFuture.completedFuture(targetFile);
        }
        String downloadUrl = MAVEN_REPO_BASE + "/" + version + "/spring-boot-" + version + suffix + ".jar";
        Path partFile = path.resolve(fileName + ".part");
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(path);
                AtomicLong contentLength = new AtomicLong();
                restClient.get().uri(URI.create(downloadUrl))
                        .exchange((_, response) -> {
                            long cl = response.getHeaders().getContentLength();
                            contentLength.set(cl);
                            InputStream input = response.getBody();
                            byte[] buffer = new byte[DOWNLOAD_BUFFER];
                            int read;
                            long downloaded = 0;
                            try (OutputStream output = Files.newOutputStream(partFile)) {
                                while ((read = input.read(buffer)) != -1) {
                                    output.write(buffer, 0, read);
                                    downloaded += read;
                                    if (progressCallback != null && cl > 0) {
                                        progressCallback.accept(Progress.of(
                                                Math.min(100.0, (downloaded * 100.0) / cl),
                                                Progress.MODULE_DOWNLOAD));
                                    }
                                }
                            }
                            if (progressCallback != null && cl <= 0) {
                                progressCallback.accept(Progress.of(100.0, Progress.MODULE_DOWNLOAD));
                            }
                            return cl;
                        });

                Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Spring Boot {} {} downloaded to {}", version, artifactType, targetFile);
                return targetFile;
            } catch (IOException e) {
                throw new RuntimeException("Failed to download Spring Boot " + version
                        + " " + artifactType + ": " + e.getMessage(), e);
            }
        }, downloadExecutor).exceptionally(ex -> {
            try {
                Files.deleteIfExists(partFile);
            } catch (IOException cleanupEx) {
                // Cleanup failure is not exceptional — swallow silently
            }
            throw new RuntimeException(
                    "Failed to download Spring Boot " + version + " " + artifactType
                            + ": " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()),
                    ex);
        });
    }

    /**
     * Downloads the main Spring Boot jar for the specified version. Convenience alias for
     * {@link #downloadArtifact(String, String, Consumer)}.
     */
    public CompletableFuture<Path> downloadMainJar(String version, Consumer<Progress> progressCallback) {
        return downloadArtifact(version, "jar", progressCallback);
    }

    /**
     * Downloads the Spring Boot sources jar for the specified version. Convenience alias for
     * {@link #downloadArtifact(String, String, Consumer)}.
     */
    public CompletableFuture<Path> downloadSourcesJar(String version, Consumer<Progress> progressCallback) {
        return downloadArtifact(version, "sources", progressCallback);
    }

}
