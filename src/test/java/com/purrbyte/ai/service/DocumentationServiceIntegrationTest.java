package com.purrbyte.ai.service;

import com.purrbyte.ai.test.BaseTest;
import com.purrbyte.ai.test.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DocumentationService} that verify the full JavaDoc generation
 * pipeline — extract the running JDK's {@code lib/src.zip}, run javadoc with the JsonDoclet in module
 * mode, and produce JSON documentation files.
 *
 * <p>Runs end-to-end with the running JDK's own complete sources and the real javadoc tool. Scoped to
 * the {@code java.base} module to keep the run fast. Skipped by default; enable with
 * {@code -Dtest.integration.enabled=true}.
 */
@Slf4j
@Tag(BaseTest.TAG_INTEGRATION)
class DocumentationServiceIntegrationTest extends IntegrationTest {

    @Test
    @Order(1)
    void generateJdkDocumentation_jdk25_0_3_producesJsonOutput() throws ExecutionException, InterruptedException {
        var service = new DocumentationService(
                Path.of("target/test-jdk-doc-workspace"),
                Path.of("target/test-javadoc-output"),
                "java.base",
                Path.of(System.getProperty("user.dir"), "doclet"),
                ""
        );
        var future = service.generateJdkDocumentation("25.0.3",
                p -> log.info("Progress [{}]: {}%", p.getModule(), p.getPercentage()));
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
}
