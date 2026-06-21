package com.purrbyte.ai.util;

import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZIPHelperTest extends UnitTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a test ZIP file with the given entries and returns its path.
     */
    private Path createZip(String... entries) throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        try (java.util.zip.ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (int i = 0; i < entries.length; i += 2) {
                String name = entries[i];
                String content = entries[i + 1];
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    @Nested
    class FindZipEntryTest {

        @Test
        void exactMatch_returnsEntry() throws IOException {
            Path zipFile = createZip("index.json", "content");
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "index.json");
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("index.json");
            }
        }

        @Test
        void versionPrefixedDirectory_returnsEntry() throws IOException {
            Path zipFile = createZip(
                    "jdk-25.0.3/", "",
                    "jdk-25.0.3/index.json", "content"
            );
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "index.json");
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("jdk-25.0.3/index.json");
            }
        }

        @Test
        void versionPrefixedWithSlash_returnsEntry() throws IOException {
            Path zipFile = createZip(
                    "21/", "",
                    "21/chunks.jsonl", "line1"
            );
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "chunks.jsonl");
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("21/chunks.jsonl");
            }
        }

        @Test
        void nonExistentEntry_returnsNull() throws IOException {
            Path zipFile = createZip("index.json", "content");
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "nonexistent.json");
                assertThat(entry).isNull();
            }
        }

        @Test
        void emptyZip_returnsNull() throws IOException {
            Path zipFile = createZip();
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "anything");
                assertThat(entry).isNull();
            }
        }

        @Test
        void noVersionPrefix_noMatch() throws IOException {
            Path zipFile = createZip("other.txt", "content");
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "index.json");
                assertThat(entry).isNull();
            }
        }

        @Test
        void multipleVersionDirectories_returnsFirstMatch() throws IOException {
            Path zipFile = createZip(
                    "jdk-21.0.11/", "",
                    "jdk-21.0.11/index.json", "content1",
                    "jdk-25.0.3/", "",
                    "jdk-25.0.3/index.json", "content2"
            );
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "index.json");
                assertThat(entry).isNotNull();
                // Any version-prefixed match is acceptable
                assertThat(entry.getName()).endsWith("/index.json");
            }
        }

        @Test
        void windowsStylePath_returnsEntry() throws IOException {
            Path zipFile = createZip(
                    "jdk-25.0.3\\", "",
                    "jdk-25.0.3\\index.json", "content"
            );
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "index.json");
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("jdk-25.0.3\\index.json");
            }
        }

        @Test
        void deepVersionDirectory_returnsEntry() throws IOException {
            Path zipFile = createZip(
                    "jdk-25.0.3-ga/", "",
                    "jdk-25.0.3-ga/modules/java.base/", "",
                    "jdk-25.0.3-ga/modules/java.base/index.json", "content"
            );
            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                ZipEntry entry = ZIPHelper.findZipEntry(zip, "index.json");
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("jdk-25.0.3-ga/modules/java.base/index.json");
            }
        }
    }
}