# Data Flow

## Overview

The MCP configuration class registers two `ToolCallbackProvider` beans that expose MCP tools to AI models. The beans are created using Spring AI's `MethodToolCallbackProvider`, which discovers `@Tool`-annotated methods via reflection.

## Sequence Diagram

```
Spring AI MCP Server startup
    │
    ├──→ MethodToolCallbackProvider.builder()
    │   └──→ .toolObjects(JavaDocMCP)
    │       └──→ Discovers: listVersions(), searchJavadoc()
    │
    └──→ MethodToolCallbackProvider.builder()
        └──→ .toolObjects(SpringBootMCP)
            └──→ Discovers: searchSpringBootDocs()
```

## Data Models

### Input

None — the beans are created programmatically.

### Output

```
ToolCallbackProvider — a Spring AI bean that exposes MCP tools
```

## Error States

- `IllegalStateException` — If the `@Tool`-annotated methods cannot be reflected on (e.g., missing dependencies)
