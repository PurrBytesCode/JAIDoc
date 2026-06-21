package com.purrbyte.ai.configuration;

import com.purrbyte.ai.mcp.JavaDocMCP;
import com.purrbyte.ai.mcp.SpringBootMCP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class McpToolsConfiguration {

    @Bean
    public ToolCallbackProvider javadocToolCallbacks(JavaDocMCP javaDocMCP) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(javaDocMCP)
                .build();
    }

    @Bean
    public ToolCallbackProvider springBootToolCallbacks(SpringBootMCP springBootMCP) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(springBootMCP)
                .build();
    }
}
