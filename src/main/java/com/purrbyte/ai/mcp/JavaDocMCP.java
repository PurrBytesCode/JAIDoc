package com.purrbyte.ai.mcp;

import com.purrbyte.ai.model.dto.JdkSearchResult;
import com.purrbyte.ai.service.DocumentationService;
import com.purrbyte.ai.service.JdkSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaDocMCP {

    private final JdkSearchService jdkSearchService;
    private final DocumentationService documentationService;

    @Tool(description = "List JDK versions whose documentation has been generated")
    public List<String> listVersions() {
        return documentationService.listAvailableVersions();
    }

    @Tool(description = "Semantic search of the JDK Javadoc within a single version")
    public List<JdkSearchResult> searchJavadoc(
            @ToolParam(description = "JDK version to search within, e.g. 25.0.3") String version,
            @ToolParam(description = "Natural language query") String query,
            @ToolParam(description = "Maximum number of results") int topK) {
        return jdkSearchService.search(version, query, topK <= 0 ? 10 : topK);
    }

    @Tool(description = "Generate JDK documentation and ingest it into the database. Downloads the JDK source, runs the JsonDoclet to produce JSON Javadoc, then ingests the data with vector embeddings.")
    public String ingestJdk(@ToolParam(description = "JDK version to ingest, e.g. 25.0.3") String version) {
        // TODO: Implement actual search logic
        return "Not Implemented";
    }
}
