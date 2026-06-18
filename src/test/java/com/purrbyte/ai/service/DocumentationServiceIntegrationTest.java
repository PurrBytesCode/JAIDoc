package com.purrbyte.ai.service;

import com.purrbyte.ai.model.dto.Progress;
import com.purrbyte.ai.test.BaseTest;
import com.purrbyte.ai.test.IntegrationTest;
import com.purrbyte.ai.util.JdkDistributionDownloader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DocumentationService} that verify the full Javadoc generation
 * pipeline — obtain a complete {@code lib/src.zip}, run Javadoc with the JsonDoclet in module mode, and
 * produce JSON documentation files.
 *
 * <p>The first test documents the running JDK from its local {@code lib/src.zip}; the second downloads
 * a non-running version's distribution from Adoptium and documents that. Both are scoped to the
 * {@code java.base} module to keep the run fast. Skipped by default; enable with
 * {@code -Dtest.integration.enabled=true}. The download test additionally needs network access.
 */
@Slf4j
@Tag(BaseTest.TAG_INTEGRATION)
class DocumentationServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private JdkDistributionDownloader distributionDownloader;

    private final Consumer<Progress> progressCallback = progress -> log.info("Progress [{}]: {}%", progress.getModule(), progress.getPercentage());

    @Test
    @Order(1)
    void generateJdkDocumentation_jdk25_0_3_producesJsonOutput() throws ExecutionException, InterruptedException {
        var service = new DocumentationService(
                distributionDownloader,
                Path.of("target/test-jdk-doc-workspace"),
                Path.of("target/test-javadoc-output"),
                "java.base",
                Path.of(System.getProperty("user.dir"), "doclet"),
                ""
        );
        var future = service.generateJdkDocumentation("25.0.3", progressCallback);
        Path result = future.get();
        assertThat(result).isNotNull();
        assertThat(result).isDirectory();
        assertThat(result.getFileName().toString()).isEqualTo("25.0.3");
        assertThat(Files.exists(result.resolve("index.json"))).isTrue();
        assertThat(Files.isDirectory(result.resolve("api"))).isTrue();
        String indexContent;
        try {
            indexContent = Files.readString(result.resolve("index.json"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read index.json", e);
        }
        assertThat(indexContent).isNotBlank();
        assertThat(indexContent).contains("\"version\"");
        assertThat(indexContent).contains("\"packages\"");
    }

    @Test
    @Order(2)
    void generateJdkDocumentation_downloadsNonRunningVersion_producesJsonOutput() throws ExecutionException, InterruptedException {
        // 21.x is a modular JDK that is not the running JDK (25) → triggers an Adoptium download.
        String version = "21.0.11";
        var service = new DocumentationService(
                distributionDownloader,
                Path.of("target/test-jdk-doc-workspace"),
                Path.of("target/test-javadoc-output"),
                "java.base",
                Path.of(System.getProperty("user.dir"), "doclet"),
                ""
        );
        var future = service.generateJdkDocumentation(version, progressCallback);
        Path result = future.get();
        assertThat(result).isDirectory();
        assertThat(result.getFileName().toString()).isEqualTo(version);
        assertThat(Files.exists(result.resolve("index.json"))).isTrue();
        assertThat(Files.isDirectory(result.resolve("api"))).isTrue();
        String indexContent;
        try {
            indexContent = Files.readString(result.resolve("index.json"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read index.json", e);
        }
        assertThat(indexContent).contains("\"version\" : \"" + version + "\"");
    }
}
