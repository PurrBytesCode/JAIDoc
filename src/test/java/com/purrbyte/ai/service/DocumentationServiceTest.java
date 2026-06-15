package com.purrbyte.ai.service;

import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationServiceTest extends UnitTest {

    @TempDir
    Path tempDir;

    private Path workDirectory;
    private Path outputDirectory;
    private Path docletDirectory;

    private void setupDirectories() throws IOException {
        workDirectory = tempDir.resolve("work");
        outputDirectory = tempDir.resolve("data");
        docletDirectory = tempDir.resolve("doclet");
        Files.createDirectories(workDirectory);
        Files.createDirectories(outputDirectory);
        Files.createDirectories(docletDirectory);
    }

    @Nested
    class ResolveDocletJarPathTest {

        @Test
        void noDocletJarInDocletDir_returnsNull() throws IOException {
            setupDirectories();
            // docletDirectory is an empty temp dir — no JAIDoc-doclet.jar present
            DocumentationService service = createService();
            String result = service.resolveDocletPath();
            assertThat(result).isNull();
        }

        @Test
        void docletJarInDocletDir_returnsIt() throws IOException {
            setupDirectories();
            Files.writeString(docletDirectory.resolve("JAIDoc-doclet.jar"), "fake-jar");
            DocumentationService service = createService();
            String result = service.resolveDocletPath();
            assertThat(result).isEqualTo(docletDirectory.resolve("JAIDoc-doclet.jar").toString());
        }
    }

    @Nested
    class ListAvailableVersionsTest {

        @Test
        void noVersions_returnsEmptyList() throws IOException {
            setupDirectories();
            DocumentationService service = createService();
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).isEmpty();
        }

        @Test
        void oneVersionWithIndex_returnsIt() throws IOException {
            setupDirectories();
            Path versionDir = outputDirectory.resolve("25.0.3");
            Files.createDirectories(versionDir);
            Files.writeString(versionDir.resolve("index.json"), "{}");
            DocumentationService service = createService();
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).containsExactly("25.0.3");
        }

        @Test
        void twoVersions_returnsSorted() throws IOException {
            setupDirectories();
            Path v21Dir = outputDirectory.resolve("21.0.11");
            Files.createDirectories(v21Dir);
            Files.writeString(v21Dir.resolve("index.json"), "{}");
            Path v25Dir = outputDirectory.resolve("25.0.3");
            Files.createDirectories(v25Dir);
            Files.writeString(v25Dir.resolve("index.json"), "{}");
            DocumentationService service = createService();
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).containsExactly("21.0.11", "25.0.3");
        }

        @Test
        void versionWithoutIndex_ignored() throws IOException {
            setupDirectories();
            Path incompleteDir = outputDirectory.resolve("25.0.3");
            Files.createDirectories(incompleteDir);
            // No index.json — should be ignored
            DocumentationService service = createService();
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).isEmpty();
        }
    }

    @Nested
    class IsVersionGeneratedTest {

        @Test
        void versionWithIndex_returnsTrue() throws IOException {
            setupDirectories();
            Path versionDir = outputDirectory.resolve("25.0.3");
            Files.createDirectories(versionDir);
            Files.writeString(versionDir.resolve("index.json"), "{}");
            DocumentationService service = createService();
            assertThat(service.isVersionGenerated("25.0.3")).isTrue();
        }

        @Test
        void versionWithoutIndex_returnsFalse() throws IOException {
            setupDirectories();
            DocumentationService service = createService();
            assertThat(service.isVersionGenerated("25.0.3")).isFalse();
        }

        @Test
        void nonExistentVersion_returnsFalse() throws IOException {
            setupDirectories();
            DocumentationService service = createService();
            assertThat(service.isVersionGenerated("99.0.0")).isFalse();
        }
    }

    @Nested
    class GetVersionDirTest {

        @Test
        void existingVersion_returnsPath() throws IOException {
            setupDirectories();
            Path versionDir = outputDirectory.resolve("25.0.3");
            Files.createDirectories(versionDir);
            DocumentationService service = createService();
            assertThat(service.getVersionDir("25.0.3")).isEqualTo(versionDir);
        }

        @Test
        void nonExistentVersion_returnsNull() throws IOException {
            setupDirectories();
            DocumentationService service = createService();
            assertThat(service.getVersionDir("25.0.3")).isNull();
        }
    }

    @Nested
    class ExtractSourceZipTest {

        @Test
        void extract_returnsExtractDirWithContents() throws Exception {
            setupDirectories();
            Path zipFile = createFakeJdkZip("jdk-25.0.3-ga");
            DocumentationService service = createService();
            Path result = invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            Path extractDir = workDirectory.resolve("jdk-sources").resolve("25.0.3");
            assertThat(result).isEqualTo(extractDir);
            assertThat(Files.exists(result.resolve("jdk-25.0.3-ga").resolve("Test.java"))).isTrue();
        }

        @Test
        void idempotent_returnsExistingDir() throws Exception {
            setupDirectories();
            Path extractDir = workDirectory.resolve("jdk-sources").resolve("25.0.3");
            Files.createDirectories(extractDir.resolve("jdk-25.0.3-ga"));
            DocumentationService service = createService();
            Path zipFile = createFakeJdkZip("jdk-25.0.3-ga");
            Path result1 = invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            assertThat(result1).isEqualTo(extractDir);
            // Second call should return the existing dir (idempotent)
            Path result2 = invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            assertThat(result2).isEqualTo(extractDir);
        }

        @Test
        void zipSlipEntry_skipped() throws Exception {
            setupDirectories();
            Path zipFile = createZipWithZipSlipEntry();
            DocumentationService service = createService();
            // Zip-slip entry should be skipped, extraction should succeed
            Path result = invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            assertThat(result).isEqualTo(workDirectory.resolve("jdk-sources").resolve("25.0.3"));
            // The evil.txt should NOT exist
            Path evilFile = Path.of("/etc/evil.txt");
            assertThat(Files.exists(evilFile)).isFalse();
        }
    }

    private DocumentationService createService() {
        return new DocumentationService(null, workDirectory, outputDirectory, "", docletDirectory, "");
    }

    private Path invokeExtractSourceZip(DocumentationService service, Path zipFile, String version, Consumer<Double> progressCallback) throws Exception {
        Method method = DocumentationService.class.getDeclaredMethod("extractSourceZip", Path.class, String.class, Consumer.class);
        method.setAccessible(true);
        return (Path) method.invoke(service, zipFile, version, progressCallback);
    }

    private Path createFakeJdkZip(String sourceDirName) throws IOException {
        Path zipFile = tempDir.resolve("jdk-25.0.3.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            ZipEntry dirEntry = new ZipEntry(sourceDirName + "/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
            ZipEntry fileEntry = new ZipEntry(sourceDirName + "/Test.java");
            zos.putNextEntry(fileEntry);
            zos.write("public class Test {}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zipFile;
    }

    private Path createZipWithZipSlipEntry() throws IOException {
        Path zipFile = tempDir.resolve("jdk-slip.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            ZipEntry safeEntry = new ZipEntry("jdk-25.0.3-ga/");
            zos.putNextEntry(safeEntry);
            zos.closeEntry();
            ZipEntry maliciousEntry = new ZipEntry("../../../../etc/evil.txt");
            zos.putNextEntry(maliciousEntry);
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zipFile;
    }
}
