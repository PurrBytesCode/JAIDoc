package com.purrbyte.ai.test.extension;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@Slf4j
public class TimeExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String START_TIME = "start_time";

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        getStore(context).put(START_TIME, System.currentTimeMillis());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        log.info("Method: {} took {} MS", context.getRequiredTestMethod().getName(), System.currentTimeMillis() - getStore(context).remove(START_TIME, Long.class));
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
