package com.purrbyte.ai.test;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@Tag(BaseTest.TAG_INTEGRATION)
@SpringBootTest
public abstract class IntegrationTest extends BaseTest {

    @Autowired
    protected JsonMapper jsonMapper;
}
