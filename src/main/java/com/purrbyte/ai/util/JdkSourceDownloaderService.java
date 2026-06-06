package com.purrbyte.ai.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for downloading JDK source code ZIP archives from GitHub.
 * Supports versions like 25.0.1, 25.0.3, 21.0.2, etc.
 */
@Slf4j
@Service
public class JdkSourceDownloaderService {

    private static final String GITHUB_BASE_URL = "https://github.com/openjdk/jdk/archive/refs/tags/";
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_RETRIES = 3;

    /**
     * Listener for download progress updates.
     */
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes, double percent);
        void onComplete(Path destinationPath);
        void onError(Throwable error);
    }

    private final int timeoutSeconds;
    private final int maxRetries;
    private final ProgressListener progressListener;

    /**
     * Create a downloader with default settings.
     */
    public JdkSourceDownloaderService() {
        this(DEFAULT_TIMEOUT_SECONDS, null);
    }

    /**
     * Create a downloader with custom settings.
     *
     * @param timeoutSeconds connection and read timeout in seconds
     * @param progressListener optional progress callback
     */
    public JdkSourceDownloaderService(int timeoutSeconds, ProgressListener progressListener) {
        this.timeoutSeconds = timeoutSeconds;
        this.progressListener = progressListener;
        this.maxRetries = MAX_RETRIES;
    }

    /**
     * Download JDK source code ZIP for the given version.
     *
     * @param version JDK version (e.g., "25.0.1", "25.0.3")
     * @param destinationDir directory where the ZIP will be saved
     * @return path to the downloaded ZIP file
     * @throws IOException if download fails after all retry attempts
     * @throws InterruptedException if the thread is interrupted during retry backoff
     */
    public Path downloadSource(String version, Path destinationDir) throws IOException, InterruptedException {
        return downloadSource(version, destinationDir, null);
    }

    /**
     * Download JDK source code ZIP with progress tracking.
     *
     * @param version JDK version (e.g., "25.0.1", "25.0.3")
     * @param destinationDir directory where the ZIP will be saved
     * @param listener optional progress callback
     * @return path to the downloaded ZIP file
     * @throws IOException if download fails after all retry attempts
     * @throws InterruptedException if the thread is interrupted during retry backoff
     */
    public Path downloadSource(String version, Path destinationDir, ProgressListener listener)
            throws IOException, InterruptedException {

        Path zipPath = resolveZipPath(version, destinationDir);

        if (Files.exists(zipPath) && isValidFile(zipPath)) {
            log.info("Source file already exists: {} — skipping download", zipPath);
            return zipPath;
        }

        String downloadUrl = resolveDownloadUrl(version);
        Path finalPath = zipPath;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Downloading JDK source v{} from {} (attempt {}/{})",
                        version, downloadUrl, attempt, maxRetries);
                return executeDownload(downloadUrl, finalPath, listener);
            } catch (IOException | InterruptedException e) {
                log.warn("Download attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    throw new IOException(
                            "Failed to download JDK source v" + version
                                    + " after " + maxRetries + " attempts",
                            e);
                }
                Thread.sleep(1000L * attempt);
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    /**
     * Extract the downloaded ZIP to a target directory.
     *
     * @param zipPath path to the ZIP file
     * @param extractDir directory where contents will be extracted
     * @return path to the extracted source root directory
     * @throws IOException if extraction fails
     */
    public Path extractSource(Path zipPath, Path extractDir) throws IOException {
        log.info("Extracting {} to {}", zipPath, extractDir);
        Files.createDirectories(extractDir);
        try (var fs = java.nio.file.FileSystems.newFileSystem(
                zipPath, ClassLoader.getSystemClassLoader())) {
            Path sourceRoot = fs.getPath("/");
            Files.walk(sourceRoot)
                    .filter(Files::isRegularFile)
                    .forEach(src -> {
                        Path dest = extractDir.resolve(
                                sourceRoot.relativize(src).toString());
                        try {
                            Files.createDirectories(dest.getParent());
                            Files.copy(src, dest,
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to extract: " + src.getFileName(), e);
                        }
                    });
        }
        log.info("Extraction complete: {}", extractDir);
        return extractDir;
    }

    /**
     * Download a single file with optional progress tracking using HttpURLConnection.
     */
    private Path executeDownload(String urlStr, Path dest, ProgressListener listener)
            throws IOException, InterruptedException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "JAIDoc/0.1.0");
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestMethod("GET");

        int statusCode = connection.getResponseCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            Files.deleteIfExists(dest);
            throw new IOException("Download failed with HTTP " + statusCode);
        }

        long totalBytes = connection.getContentLengthLong();
        Path parentDir = dest.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest.toFile());
                java.nio.channels.FileChannel outputChannel = fos.getChannel();
                ReadableByteChannel inputChannel = Channels.newChannel(
                        connection.getInputStream())) {

            long downloaded = 0;
            ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            int bytesRead;

            while ((bytesRead = inputChannel.read(buffer)) != -1) {
                buffer.flip();
                outputChannel.write(buffer);
                buffer.compact();
                downloaded += bytesRead;

                if (listener != null && totalBytes > 0) {
                    double percent = (downloaded * 100.0) / totalBytes;
                    listener.onProgress(downloaded, totalBytes, percent);
                }
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }

            connection.disconnect();
        }

        long fileSize = Files.size(dest);
        if (listener != null) {
            listener.onProgress(fileSize, totalBytes > 0 ? totalBytes : fileSize, 100.0);
            listener.onComplete(dest);
        }
        log.info("Download complete: {} ({} bytes)", dest, fileSize);
        return dest;
    }

    /**
     * Resolve the GitHub download URL for a given version.
     * Tries multiple tag patterns since OpenJDK uses different naming conventions.
     *
     * @param version JDK version string (e.g., "25.0.1")
     * @return full URL to the source ZIP archive
     * @throws IllegalArgumentException if no valid URL pattern matches
     */
    private String resolveDownloadUrl(String version) {
        String[] tagPatterns = {
                "jdk-%s",
                "jdk%s",
                "%s",
                "openjdk-%s"
        };

        for (String pattern : tagPatterns) {
            String tag = String.format(pattern, version);
            String url = GITHUB_BASE_URL + tag + ".zip";
            if (isUrlAccessible(url)) {
                log.debug("Resolved URL: {} (tag: {})", url, tag);
                return url;
            }
        }

        throw new IllegalArgumentException(
                "Could not find a valid download URL for JDK version '" + version
                        + "'. Tried patterns: " + Arrays.toString(tagPatterns));
    }

    /**
     * Check if a URL returns a valid response via HEAD request.
     */
    private boolean isUrlAccessible(String urlString) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestProperty("User-Agent", "JAIDoc/0.1.0");
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate the expected filename for a given JDK version.
     *
     * @param version JDK version string
     * @return filename (e.g., "jdk-source-25-0-1.zip")
     */
    public String resolveZipFileName(String version) {
        return "jdk-source-" + version.replace('.', '-') + ".zip";
    }

    /**
     * Resolve the full path where the ZIP file will be saved.
     *
     * @param version JDK version string
     * @param destinationDir target directory
     * @return absolute path to the ZIP file
     */
    public Path resolveZipPath(String version, Path destinationDir) {
        String fileName = resolveZipFileName(version);
        return destinationDir.toAbsolutePath().resolve(fileName);
    }

    /**
     * Validate that a file is a valid ZIP archive.
     *
     * @param path path to the file to validate
     * @return true if the file is a valid ZIP, false otherwise
     */
    private boolean isValidFile(Path path) {
        try (var zip = new java.util.zip.ZipFile(path.toFile())) {
            // Attempt to enumerate entries to verify integrity
            zip.entries().hasMoreElements();
            return true;
        } catch (IOException e) {
            log.debug("Invalid ZIP file: {} — {}", path, e.getMessage());
            return false;
        }
    }
}
