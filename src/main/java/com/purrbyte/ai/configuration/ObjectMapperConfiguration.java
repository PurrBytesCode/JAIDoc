package com.purrbyte.ai.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.XmlMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ObjectMapperConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return jsonMapperBuilder -> {
            log.info("Applying JsonMapperBuilderCustomizer configuration");
            jsonMapperBuilder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
            jsonMapperBuilder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }

    @Bean
    public XmlMapperBuilderCustomizer xmlMapperBuilderCustomizer() {
        return xmlMapperBuilder -> {
            log.info("Applying XmlMapperBuilderCustomizer configuration");
            xmlMapperBuilder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
            xmlMapperBuilder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }

    @Bean
    public YAMLMapper yamlMapper() {
        log.info("Preparing YAMLMapper bean");
        return YAMLMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
