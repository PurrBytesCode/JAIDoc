# Data Flow

## Overview

The model classes define the domain types used throughout the application. Enums are persisted in JPA entities, DTOs are
used for data transfer between services and as MCP tool return types.

## Sequence Diagram

```
IngestionService.ingestJdkVersion()
    │
    ├──→ IngestStatus.INGESTING → JdkVersion.status
    │
    ├──→ IngestStatus.READY → JdkVersion.status  (on success)
    │
    └──→ JdkDocChunk.kind → ElementKind  (set from parsed chunk metadata)

JdkSearchService.search(version, query)
    │
    ├──→ JdkSearchResult(chunkId, kind, qualifiedType, member, signature, text, score, rawJson)
    │
    └──→ JavaDocMCP.searchJavadoc(version, query, topK) → MCP tool response
```

## Data Models

### ElementKind

```json
{
  "values": ["MODULE", "PACKAGE", "TYPE"]
}
```

### IngestStatus

```json
{
  "values": ["INGESTING", "READY", "FAILED"],
  "default": "INGESTING"
}
```

### Progress

```json
{
  "percentage": 75.5,
  "module": "javadoc"
}
```

### JdkSearchResult

```json
{
  "chunkId": "java.io.BufferedInputStream#read(byte[],int,int)",
  "kind": "METHOD",
  "qualifiedType": "java.io.BufferedInputStream",
  "member": "read",
  "signature": "read(byte[],int,int)",
  "text": "Reads up to len bytes of data from this input stream...",
  "score": 0.847,
  "rawJson": "{\"type\": \"class\", ...}"
}
```

## Error States

- None — these are pure data types with no logic, so no error states.
