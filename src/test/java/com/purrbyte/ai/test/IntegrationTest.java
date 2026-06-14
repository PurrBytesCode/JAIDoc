package com.purrbyte.ai.test;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Tag(BaseTest.TAG_INTEGRATION)
@EnabledIf("${test.integration.enabled:false}")
public abstract class IntegrationTest extends BaseTest {

    @Autowired
    protected JsonMapper jsonMapper;
}
