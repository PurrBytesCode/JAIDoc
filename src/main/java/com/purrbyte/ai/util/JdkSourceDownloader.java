package com.purrbyte.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JdkSourceDownloader {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^jdk-\\d+(\\.\\d+)*$");

    private final Executor downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final String defaultJDKSourceDirectory;
    private final RestClient restClient;

    public JdkSourceDownloader(@Value("${jdk.source.download.directory}") String defaultJDKSourceDirectory, RestClient.Builder builder) {
        this.defaultJDKSourceDirectory = defaultJDKSourceDirectory;
        this.restClient = builder.build();
    }

    public CompletableFuture<Path> downloadSource(String version, Consumer<Double> progressCallback) {
        return downloadSource(version, Path.of(defaultJDKSourceDirectory), progressCallback);
    }

    public CompletableFuture<Path> downloadSource(String version, Path targetDirectory, Consumer<Double> progressCallback) {
        String normalizedVersion = normalizeVersion(version);
        String tagName = "jdk-" + normalizedVersion;
        String zipUrl = "https://github.com/openjdk/jdk/archive/refs/tags/" + tagName + ".zip";
        Path targetFile = targetDirectory.resolve("jdk-" + normalizedVersion + ".zip");
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = restClient.get()
                    .uri(URI.create(zipUrl))
                    .retrieve()
                    .body(InputStream.class)) {
                if (input == null) {
                    throw new IOException("Empty response from server");
                }
                Files.createDirectories(targetDirectory);
                long total = input.available();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bufferBytes = new byte[8192];
                int read;
                while ((read = input.read(bufferBytes)) != -1) {
                    byteArrayOutputStream.write(bufferBytes, 0, read);
                    if (total > 0 && progressCallback != null) {
                        double progress = (double) byteArrayOutputStream.size()
                                / total * 100.0;
                        progressCallback.accept(progress);
                    }
                }

                Files.write(targetFile, byteArrayOutputStream.toByteArray());
                return targetFile;

            } catch (Exception e) {
                throw new CompletionException(new IOException("Failed to download JDK source for version: " + version, e));
            }
        }, downloadExecutor);
    }

    /**
     * Lists the available JDK versions in the OpenJDK repository.
     *
     * @return a list of version strings, sorted in descending order by tag name
     */
    public List<String> listAvailableVersions() {
        String githubTagsApi = "https://api.github.com/repos/openjdk/jdk/tags";
        String body;
        List<String> versions = new ArrayList<>();
        try {
            body = restClient.get()
                    .uri(URI.create(githubTagsApi))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch tags from GitHub", e);
            return versions;
        }
        if (body == null || body.isBlank()) {
            log.error("Empty response from GitHub tags API");
            return versions;
        }
        Pattern pattern = Pattern.compile("\"name\"\\s*:\\s*\"(jdk-[^\"]+)\"");
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            String tag = matcher.group(1);
            versions.add(tag.substring(5));
        }
        versions.sort(Comparator.reverseOrder());
        return versions;
    }

    /**
     * Normalizes a version string by verifying it matches the OpenJDK tag naming scheme.
     *
     * @param version raw version (e.g. "17.0.1" or "jdk-25.0.3")
     * @return the version without the leading "jdk-" prefix if present
     * @throws IllegalArgumentException if the version does not conform to the expected pattern
     */
    static String normalizeVersion(String version) {
        if (version != null && VERSION_PATTERN.matcher(version).matches()) {
            return version.substring(4);
        }
        if (version != null && version.matches("\\d+(\\.\\d+)*")) {
            return version;
        }
        throw new IllegalArgumentException("Invalid version format: " + version);
    }
}
