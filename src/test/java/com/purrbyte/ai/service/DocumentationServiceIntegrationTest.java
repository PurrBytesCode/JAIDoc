package com.purrbyte.ai.service;

import com.purrbyte.ai.model.dto.Progress;
import com.purrbyte.ai.repository.JdkVersionRepository;
import com.purrbyte.ai.test.IntegrationTest;
import com.purrbyte.ai.util.JdkDistributionDownloader;
import com.purrbyte.ai.util.ZIPHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

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
class DocumentationServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private JdkDistributionDownloader distributionDownloader;

    @Autowired
    private JdkVersionRepository jdkVersionRepository;

    private final Consumer<Progress> progressCallback = progress -> log.info("Progress [{}]: {}%", progress.getModule(), progress.getPercentage());

    @Test
    @Order(1)
    void generateJdkDocumentation_jdk25_0_3_producesJsonOutput() throws ExecutionException, InterruptedException {
        var service = new DocumentationService(
                distributionDownloader,
                jdkVersionRepository,
                Path.of("target/test-jdk-doc-workspace"),
                Path.of("target/data"),
                "java.base",
                Path.of(System.getProperty("user.dir"), "doclet"),
                ""
        );
        var future = service.generateJdkDocumentation("25.0.3", progressCallback);
        Path result = future.get();
        // After generation, versionDir is returned (may have been compressed to ZIP)
        assertThat(result).isNotNull();
        // The ZIP file should exist
        Path zipPath = result.resolveSibling("25.0.3.zip");
        assertThat(Files.exists(zipPath)).as("ZIP should exist at " + zipPath).isTrue();
        // Verify the ZIP contains index.json (maybe under a version-prefixed directory)
        try {
            try (ZipFile zf = new ZipFile(zipPath.toFile())) {
                assertThat(ZIPHelper.findZipEntry(zf, "index.json")).isNotNull();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ZIP: " + zipPath, e);
        }
    }

    @Test
    @Order(2)
    void generateJdkDocumentation_downloadsNonRunningVersion_producesJsonOutput() throws ExecutionException, InterruptedException {
        // 21.x is a modular JDK that is not the running JDK (25) → triggers an Adoptium download.
        String version = "21.0.11";
        var service = new DocumentationService(
                distributionDownloader,
                jdkVersionRepository,
                Path.of("target/test-jdk-doc-workspace"),
                Path.of("target/data"),
                "java.base",
                Path.of(System.getProperty("user.dir"), "doclet"),
                ""
        );
        var future = service.generateJdkDocumentation(version, progressCallback);
        Path result = future.get();
        // The ZIP file should exist
        Path zipPath = result.resolveSibling(version + ".zip");
        assertThat(Files.exists(zipPath)).as("ZIP should exist at " + zipPath).isTrue();
        // Verify the ZIP contains index.json (maybe under a version-prefixed directory)
        try {
            try (ZipFile zf = new ZipFile(zipPath.toFile())) {
                assertThat(ZIPHelper.findZipEntry(zf, "index.json")).isNotNull();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ZIP: " + zipPath, e);
        }
    }
}
