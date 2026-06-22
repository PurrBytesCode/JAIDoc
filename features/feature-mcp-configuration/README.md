---
name: feature-mcp-configuration
status: implemented
date: 2026-06-21
---

# MCP Configuration

> Registers MCP tool callbacks as Spring beans via MethodToolCallbackProvider.

## Context

The MCP configuration class creates two `ToolCallbackProvider` beans — one for JDK documentation tools and one for
Spring Boot documentation tools. Each bean uses Spring AI's `MethodToolCallbackProvider` to discover `@Tool`-annotated
methods from the corresponding MCP component (`JavaDocMCP` and `SpringBootMCP`).

The MCP server infrastructure (configured in `mcp-configuration.yml`) picks up these beans automatically and exposes
them as MCP tools to AI models.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/configuration/McpToolsConfiguration.java` — The configuration class
- `src/main/java/com/purrbyte/ai/mcp/JavaDocMCP.java` — JDK documentation MCP tools
- `src/main/java/com/purrbyte/ai/mcp/SpringBootMCP.java` — Placeholder Spring Boot MCP tool

## Scope

In: ToolCallbackProvider bean creation, MethodToolCallbackProvider pattern
Out: Tool definitions (covered in MCP Tools), MCP server protocol (covered in Configuration)

## Implementation

### Architecture

The configuration class is a Spring `@Configuration` with two `@Bean` methods. Each creates a
`MethodToolCallbackProvider` from the corresponding MCP component, which discovers all `@Tool`-annotated methods.

### Files

- `src/main/java/com/purrbyte/ai/configuration/McpToolsConfiguration.java` — The configuration class

### Data Flow

```
Spring AI MCP Server startup
    │
    ├──→ MethodToolCallbackProvider.builder()
    │   └──→ .toolObjects(JavaDocMCP)
    │       └──→ Discovers @Tool methods: listVersions(), searchJavadoc()
    │
    └──→ MethodToolCallbackProvider.builder()
        └──→ .toolObjects(SpringBootMCP)
            └──→ Discovers @Tool method: searchSpringBootDocs()
```

### Configuration

None — the beans are registered programmatically. The MCP server protocol configuration is in `mcp-configuration.yml`.

## Tests

No dedicated unit tests exist for the MCP Configuration. It is implicitly tested through the integration tests that
exercise the full MCP tool pipeline.

## Notes

- The `MethodToolCallbackProvider` uses reflection to discover `@Tool`-annotated methods — no explicit method
  registration is needed.
- The MCP server protocol configuration (streamable protocol, etc.) is in `mcp-configuration.yml`, not in this class.
