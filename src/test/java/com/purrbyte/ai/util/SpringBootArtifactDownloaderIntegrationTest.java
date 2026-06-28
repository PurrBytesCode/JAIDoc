package com.purrbyte.ai.util;

import com.purrbyte.ai.model.dto.Progress;
import com.purrbyte.ai.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringBootArtifactDownloaderIntegrationTest extends IntegrationTest {

    @Autowired
    private SpringBootArtifactDownloader downloader;

    @Value("${spring.boot.artifacts.download.directory}")
    private Path downloadDirectory;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure the download directory exists and is clean for this test run
        if (Files.exists(downloadDirectory)) {
            try (var stream = Files.list(downloadDirectory)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        // Ignore cleanup failures
                    }
                });
            }
        }
        Files.createDirectories(downloadDirectory);
    }

    @Test
    void resolvesLatestVersionFromMavenCentral() {
        // Verifies that the downloader can resolve a version from Maven Central's metadata XML
        Optional<String> result = downloader.resolveLatestVersion();
        assertThat(result).isPresent();
        String version = result.get();
        assertThat(version).matches("\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    void downloadsMainJarSuccessfully() throws Exception {
        // Downloads the main Spring Boot jar and verifies it exists
        CompletableFuture<Path> future = downloader.downloadMainJar("3.4.2", null);
        Path downloaded = future.get();
        assertThat(downloaded).isNotNull();
        assertThat(Files.exists(downloaded)).isTrue();
        assertThat(downloaded.getFileName().toString()).isEqualTo("spring-boot-3.4.2.jar");
        assertThat(Files.size(downloaded)).isGreaterThan(0);
    }

    @Test
    void downloadsSourcesJarSuccessfully() throws Exception {
        // Downloads the sources' jar and verifies it exists
        CompletableFuture<Path> future = downloader.downloadSourcesJar("3.4.2", null);
        Path downloaded = future.get();
        assertThat(downloaded).isNotNull();
        assertThat(Files.exists(downloaded)).isTrue();
        assertThat(downloaded.getFileName().toString()).isEqualTo("spring-boot-3.4.2-sources.jar");
        assertThat(Files.size(downloaded)).isGreaterThan(0);
    }

    @Test
    void cachesDownloadedArtifact() throws Exception {
        // Verifies that already-downloaded artifacts are reused without re-downloading
        CompletableFuture<Path> first = downloader.downloadMainJar("3.4.2", null);
        Path firstPath = first.get();
        CompletableFuture<Path> second = downloader.downloadMainJar("3.4.2", null);
        Path secondPath = second.get();
        assertThat(firstPath).isEqualTo(secondPath);
    }

    @Test
    void reportsProgressDuringDownload() throws Exception {
        // Verifies that progress callbacks are invoked during download
        var progressUpdates = new java.util.concurrent.atomic.AtomicReference<Progress>();
        var latch = new java.util.concurrent.CountDownLatch(1);
        CompletableFuture<Path> future = downloader.downloadMainJar("3.4.2", progress -> {
            progressUpdates.set(progress);
            if (progress.getPercentage() >= 100.0) {
                latch.countDown();
            }
        });
        Path downloaded = future.get();
        assertThat(downloaded).isNotNull();
        // Wait for progress updates (timeout after 30 seconds)
        boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(progressUpdates.get()).isNotNull();
        assertThat(progressUpdates.get().getModule()).isEqualTo(Progress.MODULE_DOWNLOAD);
    }

    @Test
    void failsForNonExistentVersion() {
        // Verifies that requesting a non-existent version fails gracefully
        CompletableFuture<Path> future = downloader.downloadArtifact("99.99.99", "jar", null);
        assertThatThrownBy(future::get).hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void failsForNullVersion() {
        // Verifies that null version fails with IllegalArgumentException
        CompletableFuture<Path> future = downloader.downloadArtifact(null, "jar", null);
        assertThatThrownBy(future::get).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsForBlankVersion() {
        // Verifies that blank version fails with IllegalArgumentException
        CompletableFuture<Path> future = downloader.downloadArtifact("  ", "jar", null);
        assertThatThrownBy(future::get).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsForUnknownArtifactType() {
        // Verifies that unknown artifact type fails with IllegalArgumentException
        CompletableFuture<Path> future = downloader.downloadArtifact("3.4.2", "debug", null);
        assertThatThrownBy(future::get).hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
