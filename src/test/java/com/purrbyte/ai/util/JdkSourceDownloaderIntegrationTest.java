package com.purrbyte.ai.util;

import com.purrbyte.ai.test.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
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
@Slf4j
class JdkSourceDownloaderIntegrationTest extends IntegrationTest {

    @Test
    void downloadSource_jdk8_returnsFilePath() throws ExecutionException, InterruptedException {
        JdkSourceDownloader downloader = createDownloader();
        CompletableFuture<Path> future = downloader.downloadSource("8.0.492", p -> log.info("Download progress [8.0.492] {}: {}%", p.module(), p.percentage()));
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-8.0.492.zip");
    }

    @Test
    void downloadSource_jdk11_returnsFilePath() throws ExecutionException, InterruptedException {
        JdkSourceDownloader downloader = createDownloader();
        CompletableFuture<Path> future = downloader.downloadSource("11.0.28", p -> log.info("Download progress [11.0.28] {}: {}%", p.module(), p.percentage()));
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-11.0.28.zip");
    }

    @Test
    void downloadSource_jdk17_returnsFilePath() throws ExecutionException, InterruptedException {
        JdkSourceDownloader downloader = createDownloader();
        CompletableFuture<Path> future = downloader.downloadSource("17.0.13", p -> log.info("Download progress [17.0.13] {}: {}%", p.module(), p.percentage()));
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-17.0.13.zip");
    }

    @Test
    void downloadSource_jdk21_returnsFilePath() throws ExecutionException, InterruptedException {
        JdkSourceDownloader downloader = createDownloader();
        CompletableFuture<Path> future = downloader.downloadSource("21.0.11", p -> log.info("Download progress [21.0.11] {}: {}%", p.module(), p.percentage()));
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-21.0.11.zip");
    }

    @Test
    void downloadSource_jdk25_returnsFilePath() throws ExecutionException, InterruptedException {
        JdkSourceDownloader downloader = createDownloader();
        CompletableFuture<Path> future = downloader.downloadSource("25.0.1", p -> log.info("Download progress [25.0.1] {}: {}%", p.module(), p.percentage()));
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isRegularFile();
        assertThat(result.getFileName().toString()).isEqualTo("jdk-25.0.1.zip");
    }

    private JdkSourceDownloader createDownloader() {
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.@NonNull HttpURLConnection connection, @NonNull String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(true);
            }
        };
        return new JdkSourceDownloader(
                Path.of("target/test-jdk-sources").toString(),
                RestClient.builder()
                        .requestFactory(factory)
        );
    }
}
