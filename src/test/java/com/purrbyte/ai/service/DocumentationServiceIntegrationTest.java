package com.purrbyte.ai.service;

import com.purrbyte.ai.test.BaseTest;
import com.purrbyte.ai.test.IntegrationTest;
import com.purrbyte.ai.util.JdkSourceDownloader;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DocumentationService} that verify the full
 * JavaDoc generation pipeline — download JDK source, extract it, run javadoc
 * with the JsonDoclet, and produce JSON documentation files.
 *
 * <p>Runs end-to-end with the real JDK sources (downloaded from OpenJDK GitHub)
 * and the real javadoc tool. Skipped by default; enable with
 * {@code -Dtest.integration.enabled=true}.
 */
@Slf4j
@Tag(BaseTest.TAG_INTEGRATION)
class DocumentationServiceIntegrationTest extends IntegrationTest {

    @TempDir
    Path tempDirectory;

    @Test
    @Order(1)
    void generateJdkDocumentation_jdk25_0_3_producesJsonOutput() throws ExecutionException, InterruptedException {
        var downloader = createDownloader();
        var service = new DocumentationService(
                downloader,
                Path.of("target/test-jdk-doc-workspace"),
                Path.of("target/test-javadoc-output")
        );
        var future = service.generateJdkDocumentation("25.0.3", p -> log.info("Progress: {}%", p));
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isDirectory();
        assertThat(result.getFileName().toString()).isEqualTo("25.0.3");
        assertThat(Files.exists(result.resolve("index.json"))).isTrue();
        assertThat(Files.exists(result.resolve("packages.json"))).isTrue();
        String indexContent;
        try {
            indexContent = Files.readString(result.resolve("index.json"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read index.json", e);
        }
        assertThat(indexContent).isNotBlank();
        assertThat(indexContent).contains("\"version\"");
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
                tempDirectory.toString(),
                RestClient.builder()
                        .requestFactory(factory)
        );
    }
}