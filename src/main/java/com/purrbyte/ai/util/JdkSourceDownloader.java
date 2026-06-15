package com.purrbyte.ai.util;

import com.purrbyte.ai.model.Progress;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Deprecated
@Slf4j
@Component
public class JdkSourceDownloader {

    private static final Pattern JDK8_TAG_PATTERN = Pattern.compile("^jdk-8$|^jdk-8u\\d+$|^jdk-8\\d+\\.\\d+$|^jdk-8\\d+\\.\\d+\\.\\d+$");
    private static final Pattern JDK8_VERSION_PATTERN = Pattern.compile("^8$|^8u\\d+$|^8\\d+\\.\\d+$|^8\\d+\\.\\d+\\.\\d+$|^8\\.\\d+\\.\\d+$|^8\\.\\d+$");
    private static final Pattern JDK8_TAG_GA_PATTERN = Pattern.compile("^jdk8u\\d+-ga$");
    private static final Pattern MODERN_VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+)+$|^\\d+$");
    private static final Pattern MODERN_TAG_PATTERN = Pattern.compile("^jdk-\\d+(\\.\\d+)+$");

    private final Executor downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final String defaultJDKSourceDirectory;
    private final RestClient restClient;
    private final ContentLengthInterceptor contentLengthInterceptor = new ContentLengthInterceptor();

    public JdkSourceDownloader(@Value("${jdk.source.download.directory}") String defaultJDKSourceDirectory, RestClient.Builder builder) {
        this.defaultJDKSourceDirectory = defaultJDKSourceDirectory;
        this.restClient = builder
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    @Override
                    protected void prepareConnection(java.net.@NonNull HttpURLConnection connection, @NonNull String httpMethod) throws java.io.IOException {
                        super.prepareConnection(connection, httpMethod);
                        connection.setInstanceFollowRedirects(true);
                    }
                })
                .requestInterceptor(contentLengthInterceptor)
                .build();
    }

    public CompletableFuture<Path> downloadSource(String version, Consumer<Progress> progressCallback) {
        return downloadSource(version, Path.of(defaultJDKSourceDirectory), progressCallback);
    }

    public CompletableFuture<Path> downloadSource(String version, Path targetDirectory, Consumer<Progress> progressCallback) {
        String normalizedVersion = normalizeVersion(version);
        String repo = getRepoForVersion(version);
        String tagName = getTagNameForVersion(version);
        String zipUrl = "https://github.com/" + repo + "/archive/refs/tags/" + tagName + ".zip";
        Path targetFile = targetDirectory.resolve("jdk-" + normalizedVersion + ".zip");
        // Skip download if the zip already exists
        if (Files.exists(targetFile)) {
            log.info("JDK source already downloaded at {}", targetFile);
            return CompletableFuture.completedFuture(targetFile);
        }
        long total = extractContentLength(zipUrl);
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = restClient.get()
                    .uri(URI.create(zipUrl))
                    .retrieve()
                    .body(InputStream.class)) {
                if (input == null) {
                    throw new IOException("Empty response from server");
                }
                Files.createDirectories(targetDirectory);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bufferBytes = new byte[8192];
                int read;
                while ((read = input.read(bufferBytes)) != -1) {
                    byteArrayOutputStream.write(bufferBytes, 0, read);
                    if (total > 0 && progressCallback != null) {
                        double progress = (double) byteArrayOutputStream.size() / total * 100.0;
                        progressCallback.accept(Progress.of(Math.min(progress, 100.0), Progress.MODULE_DOWNLOAD));
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
     * Extracts the Content-Length header from the HTTP response using a HEAD request.
     *
     * @param url the URL to fetch headers for
     * @return the Content-Length in bytes, or -1 if not present
     */
    private long extractContentLength(String url) {
        try {
            contentLengthInterceptor.reset();
            restClient.head()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(Void.class);
            return contentLengthInterceptor.getContentLength();
        } catch (Exception e) {
            log.debug("Failed to fetch Content-Length header for {}: {}", url, e.getMessage());
            return -1L;
        }
    }

    /**
     * Returns the GitHub repository URL for a given JDK version.
     *
     * @param version the raw version string (e.g. "8", "17.0.1")
     * @return the repository path (e.g. "openjdk/jdk8u" or "openjdk/jdk17u")
     * @throws IllegalArgumentException if the version does not match a supported format
     */
    static String getRepoForVersion(String version) {
        if (isJdk8Version(version)) {
            return "openjdk/jdk8u";
        }
        return "openjdk/jdk" + extractMajorVersion(version) + "u";
    }

    /**
     * Returns the GitHub tag name for a given JDK version.
     *
     * @param version the raw version string (e.g. "8", "17.0.1")
     * @return the tag name (e.g. "jdk8u492-ga" or "jdk-17.0.13-ga")
     * @throws IllegalArgumentException if the version does not match a supported format
     */
    static String getTagNameForVersion(String version) {
        if (isJdk8Version(version)) {
            String normalized = normalizeJdk8Version(version);
            if (JDK8_TAG_GA_PATTERN.matcher(normalized).matches()) {
                return normalized;
            }
            if (normalized.startsWith("jdk-")) {
                return normalized;
            }
            // normalized is like "8u492" or "8", create tag as "jdk8u492-ga" or "jdk8u8-ga"
            if (normalized.startsWith("8u")) {
                return "jdk8u" + normalized.substring(2) + "-ga";
            }
            return "jdk8u" + normalized + "-ga";
        }
        String normalizedVersion = normalizeVersion(version);
        if (normalizedVersion.startsWith("jdk-")) {
            return normalizedVersion + "-ga";
        }
        return "jdk-" + normalizedVersion + "-ga";
    }

    /**
     * Normalizes a version string by verifying it matches the OpenJDK tag naming scheme.
     *
     * @param version raw version (e.g. "17.0.1" or "jdk-25.0.3")
     * @return the version without the leading "jdk-" prefix if present
     * @throws IllegalArgumentException if the version does not conform to the expected pattern
     */
    static String normalizeVersion(String version) {
        if (version != null && MODERN_TAG_PATTERN.matcher(version).matches()) {
            return version.substring(4);
        }
        if (version != null && JDK8_TAG_PATTERN.matcher(version).matches()) {
            return version.substring(4);
        }
        if (version != null && (isJdk8Version(version) || isModernVersion(version))) {
            return version;
        }
        throw new IllegalArgumentException("Invalid version format: " + version);
    }

    /**
     * Checks if the version string is a JDK 8 variant.
     */
    static boolean isJdk8Version(String version) {
        return JDK8_VERSION_PATTERN.matcher(version).matches() || JDK8_TAG_PATTERN.matcher(version).matches();
    }

    /**
     * Checks if the version string is a modern JDK variant (11, 17, 25, etc.).
     */
    static boolean isModernVersion(String version) {
        return MODERN_VERSION_PATTERN.matcher(version).matches() && !isJdk8Version(version);
    }

    /**
     * Normalizes a JDK 8 version string to the download tag format (e.g. "8" → "8", "8.0.412" → "8u412").
     */
    static String normalizeJdk8Version(String version) {
        if (JDK8_TAG_PATTERN.matcher(version).matches()) {
            return version;
        }
        if (version.startsWith("8.")) {
            String[] parts = version.split("\\.");
            if (parts.length >= 3) {
                return "8u" + parts[2];
            }
            return "8u" + parts[1];
        }
        return version;
    }

    /**
     * Extracts the major version number from a version string.
     */
    public static int extractMajorVersion(String version) {
        String cleanVersion = version.startsWith("jdk-") ? version.substring(4) : version;
        String[] parts = cleanVersion.split("\\.");
        return Integer.parseInt(parts[0]);
    }

    /**
     * Extracts the minor version number from a version string.
     */
    static int extractMinorVersion(String version) {
        String cleanVersion = version.startsWith("jdk-") ? version.substring(4) : version;
        String[] parts = cleanVersion.split("\\.");
        if (parts.length >= 2) {
            return Integer.parseInt(parts[1]);
        }
        return 0;
    }

    /**
     * A request interceptor that captures the Content-Length header from HTTP responses.
     */
    private static class ContentLengthInterceptor implements ClientHttpRequestInterceptor {

        private long contentLength = -1L;

        void reset() {
            contentLength = -1L;
        }

        long getContentLength() {
            return contentLength;
        }

        @Override
        public @NonNull ClientHttpResponse intercept(@NonNull HttpRequest request, byte @NonNull [] body, ClientHttpRequestExecution execution) throws java.io.IOException {
            ClientHttpResponse response = execution.execute(request, body);
            long contentLengthValue = response.getHeaders().getContentLength();
            if (contentLengthValue > 0) {
                this.contentLength = contentLengthValue;
                log.debug("Content-Length for {}: {}", request.getURI(), contentLength);
            }
            return response;
        }
    }
}
