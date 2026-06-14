package com.purrbyte.ai.util;

import com.purrbyte.ai.test.BaseTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdkSourceDownloader} that verify actual downloads from
 * the OpenJDK GitHub repositories.
 */
@Tag(BaseTest.TAG_INTEGRATION)
class JdkSourceDownloaderIntegrationTest extends BaseTest {

    private static final boolean INTEGRATION_TESTS_ENABLED = Boolean.getBoolean("integration-test-enabled");

    @TempDir
    Path tempDirectory;

    @Test
    void downloadSource_jdk8_returnsFilePath() throws ExecutionException, InterruptedException {
        Assumptions.assumeTrue(INTEGRATION_TESTS_ENABLED,
                "Integration tests disabled — skip with -Dintegration-test-enabled=true");

        JdkSourceDownloader downloader = createDownloader();

        CompletableFuture<Path> future = downloader.downloadSource("8.0.492", progress -> {});
        Path result = future.get();

        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-8.0.492.zip");
    }

    @Test
    void downloadSource_jdk11_returnsFilePath() throws ExecutionException, InterruptedException {
        Assumptions.assumeTrue(INTEGRATION_TESTS_ENABLED,
                "Integration tests disabled — skip with -Dintegration-test-enabled=true");

        JdkSourceDownloader downloader = createDownloader();

        CompletableFuture<Path> future = downloader.downloadSource("11.0.28", progress -> {});
        Path result = future.get();

        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-11.0.28.zip");
    }

    @Test
    void downloadSource_jdk17_returnsFilePath() throws ExecutionException, InterruptedException {
        Assumptions.assumeTrue(INTEGRATION_TESTS_ENABLED,
                "Integration tests disabled — skip with -Dintegration-test-enabled=true");

        JdkSourceDownloader downloader = createDownloader();

        CompletableFuture<Path> future = downloader.downloadSource("17.0.13", progress -> {});
        Path result = future.get();

        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-17.0.13.zip");
    }

    @Test
    void downloadSource_jdk21_returnsFilePath() throws ExecutionException, InterruptedException {
        Assumptions.assumeTrue(INTEGRATION_TESTS_ENABLED,
                "Integration tests disabled — skip with -Dintegration-test-enabled=true");

        JdkSourceDownloader downloader = createDownloader();

        CompletableFuture<Path> future = downloader.downloadSource("21.0.11", progress -> {});
        Path result = future.get();

        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-21.0.11.zip");
    }

    @Test
    void downloadSource_jdk25_returnsFilePath() throws ExecutionException, InterruptedException {
        Assumptions.assumeTrue(INTEGRATION_TESTS_ENABLED,
                "Integration tests disabled — skip with -Dintegration-test-enabled=true");

        JdkSourceDownloader downloader = createDownloader();

        CompletableFuture<Path> future = downloader.downloadSource("25.0.1", progress -> {});
        Path result = future.get();

        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-25.0.1.zip");
    }

    private JdkSourceDownloader createDownloader() {
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(true);
            }
        };
        return new JdkSourceDownloader(
                tempDirectory.toString(),
                RestClient.builder()
                        .requestFactory(factory)
        );
    }
}
