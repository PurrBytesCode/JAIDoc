package com.purrbyte.ai.test;

import com.purrbyte.ai.configuration.ObjectMapperConfiguration;
import org.junit.jupiter.api.Tag;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.json.JsonMapper;

@Tag(BaseTest.TAG_UNIT)
public abstract class UnitTest extends BaseTest {

    protected static final JsonMapper jsonMapper = createJsonMapper();

    private static JsonMapper createJsonMapper() {
        JsonMapper.Builder builder = new JsonMapper.Builder(new JsonFactoryBuilder().build());
        new ObjectMapperConfiguration().jsonMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }
}
