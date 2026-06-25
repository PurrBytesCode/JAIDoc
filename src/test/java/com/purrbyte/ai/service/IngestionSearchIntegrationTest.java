package com.purrbyte.ai.service;

import com.purrbyte.ai.model.dto.JdkSearchResult;
import com.purrbyte.ai.test.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class IngestionSearchIntegrationTest extends IntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JdkSearchService searchService;

    @Value("${data.directory}")
    Path dataDirectory;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test ZIP fixture at the configured output directory under jdk/.
        // The ZIP structure must match what DocumentationService.zipVersion produces:
        // a version-prefixed directory containing the JSON files.
        Path jdkDir = dataDirectory.resolve("jdk");
        Files.createDirectories(jdkDir);
        Path zipFile = jdkDir.resolve("25.0.3.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Directory entry for the version folder
            zos.putNextEntry(new ZipEntry("25.0.3/"));
            zos.closeEntry();
            // index.json inside the version directory
            String indexJson = """
                    {"javaRuntime":"temurin","generator":"JAIDoc","generatedAt":"2025-01-01T00:00:00Z",
                     "typeCount":10,"chunkCount":5,"packages":[],"modules":[]}
                    """;
            zos.putNextEntry(new ZipEntry("25.0.3/index.json"));
            zos.write(indexJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            // elements.json inside the version directory
            String elementsJson = """
                    [{"kind":"MODULE","name":"java.base","qualifiedName":"java.base"},
                     {"kind":"TYPE","name":"InputStream","qualifiedName":"java.io.InputStream","package":"java.io","module":"java.base"}]
                    """;
            zos.putNextEntry(new ZipEntry("25.0.3/elements.json"));
            zos.write(elementsJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            // chunks.jsonl inside the version directory
            String chunkLine = "{\"id\":\"c1\",\"text\":\"Read bytes from a stream\","
                    + "\"metadata\":{\"kind\":\"METHOD\",\"type\":\"java.io.InputStream\","
                    + "\"package\":\"java.io\",\"module\":\"java.base\"}}";
            zos.putNextEntry(new ZipEntry("25.0.3/chunks.jsonl"));
            zos.write(chunkLine.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    @Test
    void ingestsAndSearchesWithinVersion() throws Exception {
        // Requires a ZIP fixture under the configured data directory (created in @BeforeEach)
        ingestionService.ingest("25.0.3");
        List<JdkSearchResult> hits = searchService.search("25.0.3", "read bytes from a stream", 5);
        assertFalse(hits.isEmpty(), "expected vector hits for the ingested version");
        // Version isolation: a version that was not ingested returns anything.
        assertTrue(searchService.search("21.0.1", "read bytes from a stream", 5).isEmpty());
    }
}
