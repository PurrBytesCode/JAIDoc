package com.purrbyte.ai.util;

import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdkDistributionDownloaderTest extends UnitTest {

    @Nested
    class MapOsTest {

        @Test
        void windows() {
            assertThat(JdkDistributionDownloader.mapOs("Windows 11")).isEqualTo("windows");
        }

        @Test
        void mac() {
            assertThat(JdkDistributionDownloader.mapOs("Mac OS X")).isEqualTo("mac");
        }

        @Test
        void linux() {
            assertThat(JdkDistributionDownloader.mapOs("Linux")).isEqualTo("linux");
        }
    }

    @Nested
    class MapArchTest {

        @Test
        void amd64MapsToX64() {
            assertThat(JdkDistributionDownloader.mapArch("amd64")).isEqualTo("x64");
        }

        @Test
        void x86_64MapsToX64() {
            assertThat(JdkDistributionDownloader.mapArch("x86_64")).isEqualTo("x64");
        }

        @Test
        void aarch64() {
            assertThat(JdkDistributionDownloader.mapArch("aarch64")).isEqualTo("aarch64");
        }

        @Test
        void arm64MapsToAarch64() {
            assertThat(JdkDistributionDownloader.mapArch("arm64")).isEqualTo("aarch64");
        }
    }

    @Nested
    class ParseVersionTest {

        @Test
        void fullVersion() {
            assertThat(JdkDistributionDownloader.parseVersion("21.0.11")).containsExactly(21, 0, 11);
        }

        @Test
        void majorOnly() {
            assertThat(JdkDistributionDownloader.parseVersion("21")).containsExactly(21, -1, -1);
        }

        @Test
        void strippedJdkPrefix() {
            assertThat(JdkDistributionDownloader.parseVersion("jdk-17.0.13")).containsExactly(17, 0, 13);
        }

        @Test
        void strippedBuildSuffix() {
            assertThat(JdkDistributionDownloader.parseVersion("21.0.11+10")).containsExactly(21, 0, 11);
        }
    }

    @Nested
    class MatchesVersionTest {

        @Test
        void exactMatch() {
            var versionData = new JdkDistributionDownloader.VersionData(21, 0, 11);
            assertThat(JdkDistributionDownloader.matchesVersion(versionData, new int[]{21, 0, 11})).isTrue();
        }

        @Test
        void majorOnlyMatchesAnyPatch() {
            var versionData = new JdkDistributionDownloader.VersionData(21, 0, 5);
            assertThat(JdkDistributionDownloader.matchesVersion(versionData, new int[]{21, -1, -1})).isTrue();
        }

        @Test
        void securityMismatch() {
            var versionData = new JdkDistributionDownloader.VersionData(21, 0, 5);
            assertThat(JdkDistributionDownloader.matchesVersion(versionData, new int[]{21, 0, 11})).isFalse();
        }

        @Test
        void majorMismatch() {
            var versionData = new JdkDistributionDownloader.VersionData(17, 0, 11);
            assertThat(JdkDistributionDownloader.matchesVersion(versionData, new int[]{21, -1, -1})).isFalse();
        }
    }
}
