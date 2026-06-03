package com.purrbyte.ai.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Downloads the JDK source code from the public GitHub repository
 * (<a href="https://github.com/openjdk/jdk">Open JDK</a>) for the specified version.
 * <p>
 * Supports full versions like 25.0.1, 25.0.3, etc., each with its
 * own tag and tar.gz file in the GitHub releases.
 *
 * @author JAIDoc
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JdkSourceDownloader {

    private static final String GITHUB_RELEASES_BASE = "https://github.com/openjdk/jdk/releases/download/";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(?:\\.\\d+)?$");

    /**
     * Downloads the JDK source code for the specified version.
     *
     * @param version         the full JDK version (e.g. "25.0.1", "21.0.3")
     * @param targetDirectory the directory where the tar.gz file will be downloaded
     * @return the path to the downloaded file
     * @throws JdkSourceDownloadException if the version is invalid or the download fails
     */
    public Path downloadJdkSource(String version, Path targetDirectory) {
        validateVersion(version);
        Path resolvedDir = Objects.requireNonNull(targetDirectory, "targetDirectory cannot be null");
        try {
            if (!Files.exists(resolvedDir)) {
                Files.createDirectories(resolvedDir);
            }
        } catch (IOException e) {
            throw new JdkSourceDownloadException("Failed to create target directory: " + resolvedDir, e);
        }
        String tag = "jdk-" + version;
        String downloadUrl = GITHUB_RELEASES_BASE + tag;
        String fileName = "jdk-" + version + "-src.tar.gz";
        Path outputPath = resolvedDir.resolve(fileName);
        if (Files.exists(outputPath)) {
            throw new JdkSourceDownloadException(
                    "File already exists: " + outputPath +
                            ". Delete it or specify another target directory.");
        }
        try {
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(600_000);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new JdkSourceDownloadException(
                        "Version not found: " + version +
                                ". The tag 'jdk-" + version + "' does not exist in https://github.com/openjdk/jdk/releases");
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new JdkSourceDownloadException(
                        "Error checking download. HTTP status code: " + responseCode +
                                ". Version: " + version);
            }
            Files.copy(connection.getInputStream(), outputPath);
            return outputPath;
        } catch (IOException ex) {
            if (Files.exists(outputPath)) {
                try {
                    Files.delete(outputPath);
                } catch (IOException ignore) {
                }
            }
            throw new JdkSourceDownloadException(
                    "Failed to download JDK " + version + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Checks if a specific JDK version is available on GitHub.
     *
     * @param version the version to check (e.g. "25.0.1")
     * @return {@code true} if the tag exists in openjdk/jdk releases
     */
    public boolean isVersionAvailable(String version) {
        Objects.requireNonNull(version, "version cannot be null");
        try {
            validateVersion(version);
        } catch (JdkSourceDownloadException e) {
            return false;
        }
        String tag = "jdk-" + version;
        String url = GITHUB_RELEASES_BASE + tag;
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL()
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.connect();
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validates the format of a JDK version.
     *
     * @param version the version to validate (e.g. "25.0.1", "21.0.3")
     * @throws JdkSourceDownloadException if the format is invalid
     */
    public void validateVersionFormat(String version) {
        validateVersion(version);
    }

    private void validateVersion(String version) {
        if (version == null) {
            throw new JdkSourceDownloadException("Version cannot be null.");
        }
        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            throw new JdkSourceDownloadException("Version cannot be empty.");
        }
        if (!VERSION_PATTERN.matcher(trimmed).matches()) {
            throw new JdkSourceDownloadException(
                    "Invalid version format: '" + version + "'. " +
                            "Use numeric format with dots, e.g.: 25.0.1, 21.0.3");
        }
    }

    /**
     * Specific exception for JDK download errors.
     */
    public static final class JdkSourceDownloadException extends RuntimeException {

        JdkSourceDownloadException(String message) {
            super(message);
        }

        JdkSourceDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
