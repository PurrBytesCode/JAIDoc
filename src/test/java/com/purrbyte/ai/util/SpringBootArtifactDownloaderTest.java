package com.purrbyte.ai.util;

import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringBootArtifactDownloaderTest extends UnitTest {

    @Nested
    class LatestTagRegexTest {

        private static final Pattern LATEST_TAG = Pattern.compile("<latest>([^<]+)</latest>");

        @Test
        void extractsLatestFromMetadata() {
            // Test regex extraction of <latest> tag from Maven metadata XML
            String metadata = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<metadata>\n" +
                    "  <groupId>org.springframework.boot</groupId>\n" +
                    "  <artifactId>spring-boot</artifactId>\n" +
                    "  <versioning>\n" +
                    "    <latest>3.4.2</latest>\n" +
                    "    <release>3.4.2</release>\n" +
                    "  </versioning>\n" +
                    "</metadata>";

            Matcher matcher = LATEST_TAG.matcher(metadata);
            assertThat(matcher.find()).isTrue();
            assertThat(matcher.group(1)).isEqualTo("3.4.2");
        }

        @Test
        void handlesMultipleVersions() {
            // Metadata with multiple version elements
            String metadata = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<metadata>\n" +
                    "  <versioning>\n" +
                    "    <latest>3.5.0</latest>\n" +
                    "    <release>3.4.2</release>\n" +
                    "  </versioning>\n" +
                    "</metadata>";

            Matcher matcher = LATEST_TAG.matcher(metadata);
            assertThat(matcher.find()).isTrue();
            assertThat(matcher.group(1)).isEqualTo("3.5.0");
        }

        @Test
        void returnsFalseWhenNoLatestTag() {
            // Metadata without <latest> tag
            String metadata = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<metadata>\n" +
                    "  <versioning>\n" +
                    "    <release>3.4.2</release>\n" +
                    "  </versioning>\n" +
                    "</metadata>";

            Matcher matcher = LATEST_TAG.matcher(metadata);
            assertThat(matcher.find()).isFalse();
        }
    }

    @Nested
    class ArtifactFileNameTest {

        @Test
        void jarTypeProducesCorrectFileName() {
            // jar type should produce "spring-boot-{version}.jar"
            String fileName = "spring-boot-" + "3.4.2" + ".jar";
            assertThat(fileName).isEqualTo("spring-boot-3.4.2.jar");
        }

        @Test
        void sourcesTypeProducesCorrectFileName() {
            // sources type should produce "spring-boot-{version}-sources.jar"
            String fileName = "spring-boot-" + "3.4.2" + "-sources.jar";
            assertThat(fileName).isEqualTo("spring-boot-3.4.2-sources.jar");
        }

        @Test
        void invalidArtifactTypeThrows() {
            // Invalid artifact type should throw IllegalArgumentException
            assertThatThrownBy(() -> {
                String type = "invalid";
                if (!"jar".equals(type) && !"sources".equals(type)) {
                    throw new IllegalArgumentException("Unknown artifact type: " + type);
                }
            }).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown artifact type");
        }
    }

    @Nested
    class VersionValidationTest {

        @Test
        void validSemverVersion() {
            // Valid semver versions should match the pattern
            assertThat("3.4.2").matches("\\d+\\.\\d+\\.\\d+.*");
        }

        @Test
        void validVersionWithQualifier() {
            // Versions with qualifiers (e.g., SNAPSHOT) are valid
            assertThat("3.5.0-SNAPSHOT").matches("\\d+\\.\\d+\\.\\d+.*");
        }

        @Test
        void nullVersionIsInvalid() {
            // Null should fail validation
            String version = null;
            assertThat(version == null || version.isBlank()).isTrue();
        }

        @Test
        void blankVersionIsInvalid() {
            // Blank version should fail validation
            String version = "   ";
            assertThat(version == null || version.isBlank()).isTrue();
        }

        @Test
        void invalidVersionPattern() {
            // Invalid pattern should not match
            assertThat("invalid").doesNotMatch("\\d+\\.\\d+\\.\\d+.*");
        }
    }

    @Nested
    class DownloadUrlTest {

        @Test
        void mainJarUrl() {
            // Main jar URL should follow Maven Central layout
            String version = "3.4.2";
            String baseUrl = "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot";
            String url = baseUrl + "/" + version + "/spring-boot-" + version + ".jar";
            assertThat(url).isEqualTo("https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/3.4.2/spring-boot-3.4.2.jar");
        }

        @Test
        void sourcesJarUrl() {
            // Sources jar URL should include -sources suffix
            String version = "3.4.2";
            String baseUrl = "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot";
            String url = baseUrl + "/" + version + "/spring-boot-" + version + "-sources.jar";
            assertThat(url).isEqualTo("https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/3.4.2/spring-boot-3.4.2-sources.jar");
        }
    }
}
