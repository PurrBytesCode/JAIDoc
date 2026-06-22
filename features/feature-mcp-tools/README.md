---
name: feature-mcp-tools
status: implemented
date: 2026-06-21
---

# MCP Tools

> Model Context Protocol tools for querying JDK documentation and (placeholder) Spring Boot documentation.

## Context

The MCP tools expose the JAIDoc search functionality to AI models through the Model Context Protocol. There are two tool providers:

1. **JavaDocMCP** — Two tools: `listVersions()` to list available JDK versions, and `searchJavadoc(version, query, topK)` for semantic search within a single version
2. **SpringBootMCP** — One placeholder tool: `searchSpringBootDocs(query)` — returns a stub response (TODO: actual implementation)

The tools use Spring AI's `@Tool` annotation to define the tool description and parameters, and `@ToolParam` to describe each parameter. The `searchJavadoc()` method defaults `topK` to 10 if not specified (or if <= 0).

## Feature Inputs

- `src/main/java/com/purrbyte/ai/mcp/JavaDocMCP.java` — JDK documentation MCP tools
- `src/main/java/com/purrbyte/ai/mcp/SpringBootMCP.java` — Placeholder Spring Boot MCP tool
- `src/main/java/com/purrbyte/ai/service/JdkSearchService.java` — Search service used by searchJavadoc
- `src/main/java/com/purrbyte/ai/service/DocumentationService.java` — Documentation service used by listVersions

## Scope

In: MCP tool definitions, parameter descriptions, result types
Out: MCP protocol implementation (covered in MCP Configuration), search logic (covered in Semantic Search)

## Implementation

### Architecture

The tools are Spring `@Component` classes with `@Tool` annotated methods. The `@Tool` annotation defines the tool's description and the `@ToolParam` annotations describe each parameter. Spring AI discovers these methods and registers them as MCP tools via the `MethodToolCallbackProvider` bean.

### Files

- `src/main/java/com/purrbyte/ai/mcp/JavaDocMCP.java` — JDK documentation MCP tools
- `src/main/java/com/purrbyte/ai/mcp/SpringBootMCP.java` — Placeholder Spring Boot MCP tool

### Data Flow

```
AI Model requests tool: listVersions()
    │
    └──→ JavaDocMCP.listVersions()
            └──→ DocumentationService.listAvailableVersions()
                    └──→ List<String> — version strings ordered by major descending

AI Model requests tool: searchJavadoc(version="25.0.3", query="read bytes from a stream", topK=10)
    │
    └──→ JavaDocMCP.searchJavadoc(version, query, topK)
            └──→ JdkSearchService.search(version, query, topK)
                    └──→ List<JdkSearchResult> — search results with metadata
```

### Configuration

None — the tools are registered via Spring AI's `MethodToolCallbackProvider` (covered in MCP Configuration)

## Tests

No dedicated unit tests exist for the MCP tools. They are implicitly tested through the integration tests that exercise the full search pipeline.

## Notes

- The `searchJavadoc()` method defaults `topK` to 10 if not specified or <= 0 — this ensures a reasonable default for AI models that don't specify the parameter
- The SpringBootMCP tool is a placeholder — it returns a stub response with a TODO comment. It will be implemented when the Spring Boot adoc parsing pipeline is ready.
- The `@ToolParam` annotations provide descriptions that appear in the MCP tool schema, helping AI models understand what each parameter means
