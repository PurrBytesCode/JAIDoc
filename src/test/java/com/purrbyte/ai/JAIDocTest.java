package com.purrbyte.ai;

import com.purrbyte.ai.test.IntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

import static org.assertj.core.api.Assertions.assertThat;

class JAIDocTest extends IntegrationTest {

    @Test
    @Order(1)
    void getStartupSize_returnsExpectedValue() {
        int startupSize = JAIDoc.getStartupSize();
        assertThat(startupSize).isEqualTo(2048);
    }

    @Test
    @Order(2)
    void getStartupFilter_returnsExpectedValue() {
        String startupFilter = JAIDoc.getStartupFilter();
        assertThat(startupFilter).isEqualTo("spring.beans.instantiate");
    }

    @Test
    @Order(3)
    void prepareStartup_createsBufferingApplicationStartup() {
        BufferingApplicationStartup startup = JAIDoc.prepareStartup();
        assertThat(startup).isNotNull();
        assertThat(startup).isInstanceOf(BufferingApplicationStartup.class);
    }
}
