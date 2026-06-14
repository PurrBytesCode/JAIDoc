package com.purrbyte.ai.test;

import com.purrbyte.ai.test.extension.TimeExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(TimeExtension.class)
@ActiveProfiles({"test"})
public abstract class BaseTest {

    /**
     * JUnit 5 tag for unit tests — run with {@code mvn test -Dgroups=UNIT} in CI.
     */
    public static final String TAG_UNIT = "UNIT";

    /**
     * JUnit 5 tag for integration tests — run with {@code mvn test -Dgroups=INTEGRATION} in CI.
     * Integration tests are skipped unless the property {@code test.integration.enabled=true} is set.
     */
    public static final String TAG_INTEGRATION = "INTEGRATION";
}
