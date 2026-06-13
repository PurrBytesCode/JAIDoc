package com.purrbyte.ai.util;

import com.purrbyte.ai.test.BaseTest;
import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag(BaseTest.TAG_UNIT)
class JdkSourceDownloaderTest extends UnitTest {

    @Test
    @Order(1)
    void normalizeVersion_returnsVersionWithoutPrefix() {
        String result = JdkSourceDownloader.normalizeVersion("17.0.1");
        assertThat(result).isEqualTo("17.0.1");
    }

    @Test
    @Order(2)
    void normalizeVersion_removesJdkPrefix() {
        String result = JdkSourceDownloader.normalizeVersion("jdk-25");
        assertThat(result).isEqualTo("25");
    }

    @Test
    @Order(3)
    void normalizeVersion_withJdkPrefixAndVersion() {
        String result = JdkSourceDownloader.normalizeVersion("jdk-25.0.3");
        assertThat(result).isEqualTo("25.0.3");
    }

    @Test
    @Order(4)
    void normalizeVersion_invalidVersion_throwsException() {
        assertThatThrownBy(() -> JdkSourceDownloader.normalizeVersion("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid version format: abc");
    }

    @Test
    @Order(5)
    void normalizeVersion_null_throwsException() {
        assertThatThrownBy(() -> JdkSourceDownloader.normalizeVersion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid version format: null");
    }
}
