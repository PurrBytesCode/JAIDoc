package com.purrbyte.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

import java.util.UUID;

@Slf4j
@SpringBootApplication
public class JAIDoc {

    static void main(String[] args) {
        String appId = UUID.randomUUID().toString();
        System.setProperty("APP_ID", appId);
        log.info("APP_ID: {}", appId);
        SpringApplication springApplication = new SpringApplication(JAIDoc.class);
        springApplication.setApplicationStartup(prepareStartup());
        springApplication.run(args);
    }

    static BufferingApplicationStartup prepareStartup() {
        int startupSize = getStartupSize();
        String startupFilter = getStartupFilter();
        log.info("Preparing startup Buffer: {} - {}", startupSize, startupFilter);
        BufferingApplicationStartup bufferingApplicationStartup = new BufferingApplicationStartup(startupSize);
        bufferingApplicationStartup.addFilter(startupStep -> startupStep.getName().matches(startupFilter));
        return bufferingApplicationStartup;
    }

    static int getStartupSize() {
        return 2048;
    }

    static String getStartupFilter() {
        return "spring.beans.instantiate";
    }
}
