package com.purrbyte.ai.service;

import com.purrbyte.ai.repository.JdkVersionRepository;
import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

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
import static org.mockito.Mockito.when;

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

        private DocumentationService createServiceWithRepo(JdkVersionRepository repo) {
            return new DocumentationService(null, repo, workDirectory, outputDirectory, "", docletDirectory, "");
        }

        @Test
        void noVersions_returnsEmptyList() throws IOException {
            setupDirectories();
            JdkVersionRepository mockRepository = Mockito.mock(JdkVersionRepository.class);
            when(mockRepository.findAllVersionStringsOrderByMajorDesc()).thenReturn(List.of());
            DocumentationService service = createServiceWithRepo(mockRepository);
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).isEmpty();
        }

        @Test
        void oneVersion_returnsIt() throws IOException {
            setupDirectories();
            JdkVersionRepository mockRepository = Mockito.mock(JdkVersionRepository.class);
            when(mockRepository.findAllVersionStringsOrderByMajorDesc()).thenReturn(List.of("25.0.3"));
            DocumentationService service = createServiceWithRepo(mockRepository);
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).containsExactly("25.0.3");
        }

        @Test
        void multipleVersions_returnedInMajorDescendingOrder() throws IOException {
            setupDirectories();
            JdkVersionRepository mockRepository = Mockito.mock(JdkVersionRepository.class);
            // Simulates DB ordering: major DESC, minor DESC, security DESC
            when(mockRepository.findAllVersionStringsOrderByMajorDesc())
                    .thenReturn(List.of("25.0.3", "21.0.11"));
            DocumentationService service = createServiceWithRepo(mockRepository);
            List<String> versions = service.listAvailableVersions();
            assertThat(versions).containsExactly("25.0.3", "21.0.11");
        }
    }

    @Nested
    class IsVersionGeneratedTest {

        @Test
        void versionWithIndexInZip_returnsTrue() throws IOException {
            setupDirectories();
            createTestZipWithVersionDir(outputDirectory, "25.0.3", "index.json", "{}", "elements.json", "[]");
            DocumentationService service = createService();
            assertThat(service.isVersionGenerated("25.0.3")).isTrue();
        }

        @Test
        void versionWithIndexInZipInSubdir_returnsTrue() throws IOException {
            setupDirectories();
            Path subDir = outputDirectory.resolve("jdk");
            Files.createDirectories(subDir);
            createTestZipWithVersionDir(subDir, "25.0.3", "index.json", "{}", "elements.json", "[]");
            DocumentationService service = createService();
            assertThat(service.isVersionGenerated("25.0.3")).isTrue();
        }

        @Test
        void versionWithoutIndexInZip_returnsFalse() throws IOException {
            setupDirectories();
            // ZIP exists but no index.json inside
            createTestZipWithVersionDir(outputDirectory, "25.0.3", "elements.json", "[]");
            DocumentationService service = createService();
            assertThat(service.isVersionGenerated("25.0.3")).isFalse();
        }

        @Test
        void versionWithoutIndexInZipInSubdir_returnsFalse() throws IOException {
            setupDirectories();
            Path subDir = outputDirectory.resolve("jdk");
            Files.createDirectories(subDir);
            // ZIP exists but no index.json inside
            createTestZipWithVersionDir(subDir, "25.0.3", "elements.json", "[]");
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
    class GetVersionZipTest {

        @Test
        void existingVersionAtRoot_returnsZipPath() throws IOException {
            setupDirectories();
            createTestZipWithVersionDir(outputDirectory, "25.0.3", "index.json", "{}", "elements.json", "[]");
            DocumentationService service = createService();
            Path zipPath = outputDirectory.resolve("25.0.3.zip");
            assertThat(service.getVersionZip("25.0.3")).isEqualTo(zipPath);
        }

        @Test
        void existingVersionInSubdir_returnsZipPath() throws IOException {
            setupDirectories();
            Path subDir = outputDirectory.resolve("jdk");
            Files.createDirectories(subDir);
            createTestZipWithVersionDir(subDir, "25.0.3", "index.json", "{}", "elements.json", "[]");
            DocumentationService service = createService();
            Path zipPath = subDir.resolve("25.0.3.zip");
            assertThat(service.getVersionZip("25.0.3")).isEqualTo(zipPath);
        }

        @Test
        void nonExistentVersion_returnsNull() throws IOException {
            setupDirectories();
            DocumentationService service = createService();
            assertThat(service.getVersionZip("25.0.3")).isNull();
        }
    }

    @Nested
    class ExtractSourceZipTest {

        @Test
        void extract_createsExtractDirWithContents() throws Exception {
            setupDirectories();
            Path zipFile = createFakeJdkZip("jdk-25.0.3-ga");
            DocumentationService service = createService();
            invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            Path extractDir = workDirectory.resolve("jdk-sources").resolve("25.0.3");
            assertThat(extractDir).exists();
            assertThat(Files.exists(extractDir.resolve("jdk-25.0.3-ga").resolve("Test.java"))).isTrue();
        }

        @Test
        void idempotent_reusesExistingDir() throws Exception {
            setupDirectories();
            Path extractDir = workDirectory.resolve("jdk-sources").resolve("25.0.3");
            Files.createDirectories(extractDir.resolve("jdk-25.0.3-ga"));
            // Pre-populate the directory so we can verify the method didn't re-extract
            Files.writeString(extractDir.resolve("jdk-25.0.3-ga").resolve("Test.java"), "public class Test {}");
            Path zipFile = createFakeJdkZip("jdk-25.0.3-ga");
            DocumentationService service = createService();
            invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            // The existing directory is reused — content should not change
            assertThat(extractDir).exists();
            assertThat(Files.exists(extractDir.resolve("jdk-25.0.3-ga").resolve("Test.java"))).isTrue();
        }

        @Test
        void zipSlipEntry_skipped() throws Exception {
            setupDirectories();
            Path zipFile = createZipWithZipSlipEntry();
            DocumentationService service = createService();
            // Zip-slip entry should be skipped, extraction should succeed
            invokeExtractSourceZip(service, zipFile, "25.0.3", null);
            assertThat(workDirectory.resolve("jdk-sources").resolve("25.0.3")).exists();
            // The evil.txt should NOT exist
            Path evilFile = Path.of("/etc/evil.txt");
            assertThat(Files.exists(evilFile)).isFalse();
        }
    }

    /**
     * Creates a test ZIP with a version-prefixed directory structure, matching what
     * {@link DocumentationService#zipVersion} produces.
     */
    @SuppressWarnings("SameParameterValue")
    private Path createTestZipWithVersionDir(Path dir, String version, String... entries) throws IOException {
        Path zipFile = dir.resolve(version + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Version directory entry
            ZipEntry dirEntry = new ZipEntry(version + "/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
            for (int i = 0; i < entries.length; i += 2) {
                String name = entries[i];
                String content = entries[i + 1];
                ZipEntry entry = new ZipEntry(version + "/" + name);
                zos.putNextEntry(entry);
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    private DocumentationService createService() {
        return new DocumentationService(null, null, workDirectory, outputDirectory, "", docletDirectory, "");
    }

    @SuppressWarnings("SameParameterValue")
    private void invokeExtractSourceZip(DocumentationService service, Path zipFile, String version, Consumer<Double> progressCallback) throws Exception {
        Method method = DocumentationService.class.getDeclaredMethod("extractSourceZip", Path.class, String.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(service, zipFile, version, progressCallback);
    }

    @SuppressWarnings("SameParameterValue")
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
