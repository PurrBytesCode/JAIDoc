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
}
