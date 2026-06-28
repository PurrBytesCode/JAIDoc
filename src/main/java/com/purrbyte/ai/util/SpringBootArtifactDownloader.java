package com.purrbyte.ai.util;

import com.purrbyte.ai.model.dto.Progress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
 *   <li>{@code sources} — the sources jar used by the JsonDoclet to generate API documentation</li>
 * </ul>
 *
 * <p>Downloads are cached: an already-present file is reused. Progress is reported via a
 * {@link Consumer} of {@link Progress}. Downloads run asynchronously on virtual threads.
 */
@Slf4j
@Component
public class SpringBootArtifactDownloader {

    private static final String MAVEN_METADATA_URL =
            "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml";
    private static final String MAVEN_REPO_BASE =
            "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot";
    private static final int DOWNLOAD_BUFFER = 1 << 16;
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
     *
     * @return the latest release version string (e.g. {@code "3.4.2"})
     * @throws IOException if the metadata cannot be fetched or parsed
     */
    public String resolveLatestVersion() throws IOException {
        log.info("Resolving latest Spring Boot version from Maven Central");
        String xml = restClient.get().uri(URI.create(MAVEN_METADATA_URL)).retrieve().body(String.class);
        if (xml == null || xml.isBlank()) {
            throw new IOException("Empty response from Maven Central metadata API");
        }
        Matcher matcher = LATEST_TAG.matcher(xml);
        if (matcher.find()) {
            String latest = matcher.group(1);
            log.info("Latest Spring Boot version: {}", latest);
            return latest;
        }
        // Fallback: try the JSON search API
        return resolveLatestFromSearchApi();
    }

    /**
     * Resolves the latest version using Maven Central's Solr search API as a fallback.
     */
    private String resolveLatestFromSearchApi() throws IOException {
        String url = "https://search.maven.org/solrsearch/select?q=g:org.springframework.boot+AND+a:spring-boot"
                + "&rows=1&wt=json&core=gav";
        String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
        if (body == null) {
            throw new IOException("Empty response from Maven search API");
        }
        JsonNode root = jsonMapper.readTree(body);
        JsonNode response = root.path("response");
        JsonNode docs = response.path("docs");
        if (docs.isArray() && docs.size() > 0) {
            String version = docs.get(0).path("v").asString(null);
            log.info("Latest Spring Boot version from search API: {}", version);
            return version;
        }
        throw new IOException("No Spring Boot artifact found in Maven Central search results");
    }

    /**
     * Downloads a Spring Boot artifact for the specified version.
     *
     * @param version        Spring Boot version (e.g. {@code "3.4.2"})
     * @param artifactType   type of artifact: {@code "jar"} or {@code "sources"}
     * @param progressCallback callback reporting download progress (may be null)
     * @return future with the path to the downloaded file (cached on later calls)
     */
    public CompletableFuture<Path> downloadArtifact(String version, String artifactType,
                                                     Consumer<Progress> progressCallback) {
        validateVersion(version);
        String suffix = switch (artifactType) {
            case "jar" -> "";
            case "sources" -> "-sources";
            default -> throw new IllegalArgumentException("Unknown artifact type: " + artifactType
                    + ". Supported: jar, sources");
        };
        String fileName = "spring-boot-" + version + suffix + ".jar";
        Path targetFile = Path.of(downloadDirectory).resolve(fileName);
        if (Files.exists(targetFile)) {
            log.info("Spring Boot artifact already downloaded at {} (type={})", targetFile, artifactType);
            return CompletableFuture.completedFuture(targetFile);
        }
        String downloadUrl = MAVEN_REPO_BASE + "/" + version + "/spring-boot-" + version + suffix + ".jar";
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetDir = Path.of(downloadDirectory);
                Files.createDirectories(targetDir);
                Path partFile = targetDir.resolve(fileName + ".part");
                try (InputStream input = restClient.get().uri(URI.create(downloadUrl))
                        .retrieve().body(InputStream.class);
                     OutputStream output = Files.newOutputStream(partFile)) {
                    if (input == null) {
                        throw new IOException("Empty response downloading Spring Boot " + version + " " + artifactType);
                    }
                    byte[] buffer = new byte[DOWNLOAD_BUFFER];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        if (progressCallback != null) {
                            progressCallback.accept(Progress.of(100.0, "spring-boot-" + artifactType + "-download"));
                        }
                    }
                }
                Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Spring Boot {} {} downloaded to {}", version, artifactType, targetFile);
                return targetFile;
            } catch (Exception e) {
                // Clean up partial file on failure
                try {
                    Files.deleteIfExists(targetFile);
                    Files.deleteIfExists(targetFile.getParent().resolve(targetFile.getFileName() + ".part"));
                } catch (IOException cleanupEx) {
                    log.warn("Failed to clean up partial download: {}", cleanupEx.getMessage());
                }
                throw new CompletionException(new IOException(
                        "Failed to download Spring Boot " + version + " " + artifactType + ": " + e.getMessage(), e));
            }
        }, downloadExecutor);
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

    /**
     * Validates that the version string is non-blank.
     *
     * @throws IllegalArgumentException if version is null or blank
     */
    private static void validateVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Spring Boot version cannot be null or blank");
        }
    }
}
