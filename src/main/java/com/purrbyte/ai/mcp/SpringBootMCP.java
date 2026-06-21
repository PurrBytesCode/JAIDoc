package com.purrbyte.ai.mcp;

import org.springframework.ai.tool.annotation.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringBootMCP {

    /**
     * Search Spring Boot documentation for a given query.
     *
     * @param query the search query (e.g. "how to configure datasource")
     * @return matching documentation snippets or explanation
     */
    @Tool(description = "Search Spring Boot documentation")
    public String searchSpringBootDocs(String query) {
        log.info("Searching Spring Boot docs for: {}", query);
        // TODO: Implement actual search logic
        return "Spring Boot documentation search for: " + query;
    }
}
