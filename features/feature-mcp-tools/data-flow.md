# Data Flow

## Overview

The MCP tools expose two search capabilities to AI models: listing available JDK versions and searching JDK
documentation semantically within a single version.

## Sequence Diagram

```
listVersions()
    │
    └──→ JavaDocMCP.listVersions()
            └──→ DocumentationService.listAvailableVersions()
                    └──→ List<String> — version strings ordered by major descending

searchJavadoc(version="25.0.3", query="read bytes from a stream", topK=10)
    │
    └──→ JavaDocMCP.searchJavadoc(version, query, topK)
            └──→ JdkSearchService.search(version, query, topK)
                    └──→ List<JdkSearchResult> — search results with metadata
```

## Data Models

### listVersions Input

```
No input parameters
```

### listVersions Output

```json
["25.0.3", "21.0.11", "17.0.13"]
```

### searchJavadoc Input

```json
{
  "version": "25.0.3",
  "query": "read bytes from a stream",
  "topK": 10
}
```

### searchJavadoc Output

```json
[
  {
    "chunkId": "java.io.BufferedInputStream#read(byte[],int,int)",
    "kind": "METHOD",
    "qualifiedType": "java.io.BufferedInputStream",
    "member": "read",
    "signature": "read(byte[],int,int)",
    "text": "Reads up to len bytes of data from this input stream. An attempt to read after EOF will return -1.",
    "score": 0.847,
    "rawJson": "{\"type\": \"class\", ...}"
  }
]
```

## Error States

- None — the tools don't throw exceptions; errors are handled by the underlying services
