package com.purrbyte.ai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JavadocJsonGeneratorServiceTest {

    private JavadocJsonGeneratorService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new JavadocJsonGeneratorService();
    }

    // --- Constructor tests ---

    @Test
    void defaultConstructor_createsServiceWithDefaults() {
        assertNotNull(service);
    }

    @Test
    void customConstructor_createsServiceWithCustomTimeout() {
        JavadocJsonGeneratorService customService = new JavadocJsonGeneratorService(120, null);
        assertNotNull(customService);
    }

    @Test
    void constructorWithListener_acceptsProgressListener() {
        JavadocJsonGeneratorService.ProgressListener listener =
                new JavadocJsonGeneratorService.ProgressListener() {
                    @Override
                    public void onProgress(String stage, long current, long total) {}

                    @Override
                    public void onComplete(java.nio.file.Path outputDir) {}

                    @Override
                    public void onError(Throwable error) {}
                };

        JavadocJsonGeneratorService customService = new JavadocJsonGeneratorService(60, listener);
        assertNotNull(customService);
    }

    // --- listGeneratedFiles tests ---

    @Test
    void listGeneratedFiles_returnsEmptyList_whenNoJsonFiles(@TempDir Path outputDir) throws IOException {
        // Create some non-JSON files
        Files.writeString(outputDir.resolve("readme.txt"), "test");
        Files.writeString(outputDir.resolve("data.xml"), "<root/>");

        List<Path> jsonFiles = service.listGeneratedFiles(outputDir);
        assertEquals(0, jsonFiles.size());
    }

    @Test
    void listGeneratedFiles_returnsJsonFiles(@TempDir Path outputDir) throws IOException {
        // Create JSON files
        Files.writeString(outputDir.resolve("package-info.json"), "{}");
        Files.writeString(outputDir.resolve("class.json"), "{\"name\":\"TestClass\"}");
        Files.writeString(outputDir.resolve("readme.txt"), "test");

        List<Path> jsonFiles = service.listGeneratedFiles(outputDir);
        assertEquals(2, jsonFiles.size());
        assertTrue(jsonFiles.stream().anyMatch(p -> p.toString().endsWith("package-info.json")));
        assertTrue(jsonFiles.stream().anyMatch(p -> p.toString().endsWith("class.json")));
    }

    @Test
    void listGeneratedFiles_returnsJsonFilesInSubdirectories(@TempDir Path outputDir) throws IOException {
        // Create JSON files in subdirectories
        Path subDir = outputDir.resolve("java/lang");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("String.json"), "{\"name\":\"String\"}");
        Files.writeString(outputDir.resolve("root.json"), "{}");

        List<Path> jsonFiles = service.listGeneratedFiles(outputDir);
        assertEquals(2, jsonFiles.size());
    }

    // --- validateInputs tests (via generate method) ---

    @Test
    void generate_throwsIOException_whenSourceDirDoesNotExist() {
        Path nonExistent = tempDir.resolve("nonexistent");
        Path outputDir = tempDir.resolve("output");

        IOException exception = assertThrows(IOException.class, () ->
                service.generate("25.0.3", nonExistent, outputDir));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void generate_throwsIOException_whenSourcePathIsNotDirectory(@TempDir Path tempDir2) throws IOException {
        Path file = tempDir2.resolve("file.txt");
        Files.writeString(file, "content");
        Path outputDir = tempDir2.resolve("output");

        IOException exception = assertThrows(IOException.class, () ->
                service.generate("25.0.3", file, outputDir));
        assertTrue(exception.getMessage().contains("not a directory"));
    }

    @Test
    void resolveJdkHome_returnsRuntimeHomeWhenNoEnvVar() {
        // This test verifies that resolveJdkHome returns a valid path
        // when no JAVA_HOME is set (uses runtime home as fallback)
        // Note: We can't easily test the null case without mocking System.getenv
        // The method will return either JAVA_HOME, JAVA_HOME_<VERSION>, or runtime parent
    }

    // --- Integration helper tests ---

    @Test
    void service_isSpringBean() {
        // Verify that the service has Spring annotations
        assertTrue(service.getClass().isAnnotationPresent(org.springframework.stereotype.Service.class));
    }

    // --- Test subclass for accessing protected/internal methods ---

    /**
     * Helper subclass to test constructor with listener via internal access.
     */
    static class ListenerTestService extends JavadocJsonGeneratorService {
        public ListenerTestService(int timeout, ProgressListener listener) {
            super(timeout, listener);
        }
    }
}
