package com.purrbyte.ai.util;

import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdkSourceDownloaderTest extends UnitTest {

    @Nested
    class NormalizeVersionTest {

        @Test
        void returnsVersionWithoutPrefix() {
            String result = JdkSourceDownloader.normalizeVersion("17.0.1");
            assertThat(result).isEqualTo("17.0.1");
        }

        @Test
        void withJdkPrefixAndVersion() {
            String result = JdkSourceDownloader.normalizeVersion("jdk-25.0.3");
            assertThat(result).isEqualTo("25.0.3");
        }

        @Test
        void jdk8Tag_removesPrefix() {
            String result = JdkSourceDownloader.normalizeVersion("jdk-8u412");
            assertThat(result).isEqualTo("8u412");
        }

        @Test
        void jdk8Version_returnsAsIs() {
            String result = JdkSourceDownloader.normalizeVersion("8");
            assertThat(result).isEqualTo("8");
        }

        @Test
        void jdk8VersionWithUpdateNumber() {
            String result = JdkSourceDownloader.normalizeVersion("8u412");
            assertThat(result).isEqualTo("8u412");
        }

        @Test
        void jdk8VersionWithDotNotation() {
            String result = JdkSourceDownloader.normalizeVersion("8.0.492");
            assertThat(result).isEqualTo("8.0.492");
        }

        @Test
        void invalidVersion_throwsException() {
            assertThatThrownBy(() -> JdkSourceDownloader.normalizeVersion("abc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid version format: abc");
        }

        @Test
        void null_throwsException() {
            assertThatThrownBy(() -> JdkSourceDownloader.normalizeVersion(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid version format: null");
        }
    }

    @Nested
    class GetRepoForVersionTest {

        @Test
        void jdk8_returnsJdk8uRepo() {
            assertThat(JdkSourceDownloader.getRepoForVersion("8")).isEqualTo("openjdk/jdk8u");
            assertThat(JdkSourceDownloader.getRepoForVersion("8u492")).isEqualTo("openjdk/jdk8u");
        }

        @Test
        void jdk8_withDotNotation_returnsJdk8uRepo() {
            assertThat(JdkSourceDownloader.getRepoForVersion("8.0")).isEqualTo("openjdk/jdk8u");
            assertThat(JdkSourceDownloader.getRepoForVersion("8.0.492")).isEqualTo("openjdk/jdk8u");
        }

        @Test
        void jdk11_returnsJdk11uRepo() {
            assertThat(JdkSourceDownloader.getRepoForVersion("11")).isEqualTo("openjdk/jdk11u");
            assertThat(JdkSourceDownloader.getRepoForVersion("11.0.28")).isEqualTo("openjdk/jdk11u");
        }

        @Test
        void jdk17_returnsJdk17uRepo() {
            assertThat(JdkSourceDownloader.getRepoForVersion("17")).isEqualTo("openjdk/jdk17u");
            assertThat(JdkSourceDownloader.getRepoForVersion("17.0.1")).isEqualTo("openjdk/jdk17u");
        }

        @Test
        void jdk21_returnsJdk21uRepo() {
            assertThat(JdkSourceDownloader.getRepoForVersion("21")).isEqualTo("openjdk/jdk21u");
            assertThat(JdkSourceDownloader.getRepoForVersion("21.0.11")).isEqualTo("openjdk/jdk21u");
        }

        @Test
        void jdk25_returnsJdk25uRepo() {
            assertThat(JdkSourceDownloader.getRepoForVersion("25")).isEqualTo("openjdk/jdk25u");
            assertThat(JdkSourceDownloader.getRepoForVersion("25.0.1")).isEqualTo("openjdk/jdk25u");
        }
    }

    @Nested
    class GetTagNameForVersionTest {

        @Test
        void jdk8_returnsJdk8uGaTag() {
            assertThat(JdkSourceDownloader.getTagNameForVersion("8")).isEqualTo("jdk8u8-ga");
            assertThat(JdkSourceDownloader.getTagNameForVersion("8u412")).isEqualTo("jdk8u412-ga");
            assertThat(JdkSourceDownloader.getTagNameForVersion("8.0.492")).isEqualTo("jdk8u492-ga");
        }

        @Test
        void jdk8_jdk8TagGd_returnsAsIs() {
            assertThat(JdkSourceDownloader.getTagNameForVersion("jdk-8")).isEqualTo("jdk-8");
            assertThat(JdkSourceDownloader.getTagNameForVersion("jdk-8u412")).isEqualTo("jdk-8u412");
        }

        @Test
        void jdk11_returnsJdk11uGaTag() {
            assertThat(JdkSourceDownloader.getTagNameForVersion("11")).isEqualTo("jdk-11-ga");
            assertThat(JdkSourceDownloader.getTagNameForVersion("11.0.28")).isEqualTo("jdk-11.0.28-ga");
        }

        @Test
        void jdk17_returnsJdk17uGaTag() {
            assertThat(JdkSourceDownloader.getTagNameForVersion("17")).isEqualTo("jdk-17-ga");
            assertThat(JdkSourceDownloader.getTagNameForVersion("17.0.13")).isEqualTo("jdk-17.0.13-ga");
        }

        @Test
        void jdk21_returnsJdk21uGaTag() {
            assertThat(JdkSourceDownloader.getTagNameForVersion("21")).isEqualTo("jdk-21-ga");
            assertThat(JdkSourceDownloader.getTagNameForVersion("21.0.11")).isEqualTo("jdk-21.0.11-ga");
        }

        @Test
        void jdk25_returnsJdk25uGaTag() {
            assertThat(JdkSourceDownloader.getTagNameForVersion("25")).isEqualTo("jdk-25-ga");
            assertThat(JdkSourceDownloader.getTagNameForVersion("25.0.1")).isEqualTo("jdk-25.0.1-ga");
        }
    }

    @Nested
    class IsJdk8VersionTest {

        @Test
        void jdk8_returnsTrue() {
            assertThat(JdkSourceDownloader.isJdk8Version("8")).isTrue();
            assertThat(JdkSourceDownloader.isJdk8Version("8u492")).isTrue();
        }

        @Test
        void jdk8DotNotation_returnsTrue() {
            assertThat(JdkSourceDownloader.isJdk8Version("8.0")).isTrue();
            assertThat(JdkSourceDownloader.isJdk8Version("8.0.492")).isTrue();
        }

        @Test
        void jdk8TagPattern_returnsTrue() {
            assertThat(JdkSourceDownloader.isJdk8Version("jdk-8u412")).isTrue();
            assertThat(JdkSourceDownloader.isJdk8Version("jdk-8")).isTrue();
        }

        @Test
        void nonJdk8_returnsFalse() {
            assertThat(JdkSourceDownloader.isJdk8Version("17")).isFalse();
            assertThat(JdkSourceDownloader.isJdk8Version("17.0.1")).isFalse();
            assertThat(JdkSourceDownloader.isJdk8Version("11")).isFalse();
        }
    }

    @Nested
    class IsModernVersionTest {

        @Test
        void jdk11_returnsTrue() {
            assertThat(JdkSourceDownloader.isModernVersion("11")).isTrue();
            assertThat(JdkSourceDownloader.isModernVersion("11.0.28")).isTrue();
        }

        @Test
        void jdk17_returnsTrue() {
            assertThat(JdkSourceDownloader.isModernVersion("17")).isTrue();
            assertThat(JdkSourceDownloader.isModernVersion("17.0.13")).isTrue();
        }

        @Test
        void jdk21_returnsTrue() {
            assertThat(JdkSourceDownloader.isModernVersion("21")).isTrue();
            assertThat(JdkSourceDownloader.isModernVersion("21.0.11")).isTrue();
        }

        @Test
        void jdk25_returnsTrue() {
            assertThat(JdkSourceDownloader.isModernVersion("25")).isTrue();
            assertThat(JdkSourceDownloader.isModernVersion("25.0.1")).isTrue();
        }

        @Test
        void jdk8_returnsFalse() {
            assertThat(JdkSourceDownloader.isModernVersion("8")).isFalse();
            assertThat(JdkSourceDownloader.isModernVersion("8u492")).isFalse();
            assertThat(JdkSourceDownloader.isModernVersion("8.0.492")).isFalse();
        }
    }

    @Nested
    class NormalizeJdk8VersionTest {

        @Test
        void dottedNotation_convertsTo8uFormat() {
            assertThat(JdkSourceDownloader.normalizeJdk8Version("8.0.492")).isEqualTo("8u492");
            assertThat(JdkSourceDownloader.normalizeJdk8Version("8.0.482")).isEqualTo("8u482");
        }

        @Test
        void dottedNotationWithTwoParts_convertsTo8uFormat() {
            assertThat(JdkSourceDownloader.normalizeJdk8Version("8.0")).isEqualTo("8u0");
            assertThat(JdkSourceDownloader.normalizeJdk8Version("8.1")).isEqualTo("8u1");
        }

        @Test
        void _8uFormat_returnsAsIs() {
            assertThat(JdkSourceDownloader.normalizeJdk8Version("8u492")).isEqualTo("8u492");
        }

        @Test
        void _8_returnsAsIs() {
            assertThat(JdkSourceDownloader.normalizeJdk8Version("8")).isEqualTo("8");
        }
    }

    @Nested
    class ExtractMajorVersionTest {

        @Test
        void jdk17_returns17() {
            assertThat(JdkSourceDownloader.extractMajorVersion("17.0.1")).isEqualTo(17);
        }

        @Test
        void jdk8_returns8() {
            assertThat(JdkSourceDownloader.extractMajorVersion("8")).isEqualTo(8);
        }

        @Test
        void jdk11_returns11() {
            assertThat(JdkSourceDownloader.extractMajorVersion("11")).isEqualTo(11);
        }

        @Test
        void jdk21_returns21() {
            assertThat(JdkSourceDownloader.extractMajorVersion("21")).isEqualTo(21);
            assertThat(JdkSourceDownloader.extractMajorVersion("21.0.11")).isEqualTo(21);
        }
    }

    @Nested
    class ExtractMinorVersionTest {

        @Test
        void jdk17_withMinor_returns0() {
            assertThat(JdkSourceDownloader.extractMinorVersion("17")).isEqualTo(0);
        }

        @Test
        void jdk17_withVersion_returns0() {
            assertThat(JdkSourceDownloader.extractMinorVersion("17.0.13")).isEqualTo(0);
        }

        @Test
        void jdk11_returnsZero() {
            assertThat(JdkSourceDownloader.extractMinorVersion("11.0.28")).isEqualTo(0);
        }

        @Test
        void jdk21_returns11() {
            assertThat(JdkSourceDownloader.extractMinorVersion("21.0.11")).isEqualTo(0);
        }
    }
}
