# Data Flow

## Overview

The data models define the persistence layer for JDK documentation. A `JdkVersion` entity is created for each JDK
version, with its structural elements (`JdkDocElement`) and text chunks (`JdkDocChunk`) stored as child entities. The
chunk entity is indexed with Hibernate Search for full-text search and kNN vector search on 384-dimensional embeddings.

## Sequence Diagram

```
IngestionService.ingestJdkVersion()
    │
    ├──→ JdkVersionRepository.save(version)
    │        ↑
    │        │ Creates JdkVersion with status=INGESTING
    │
    ├──→ JdkDocElementRepository.save(element)
    │        ↑ Links to JdkVersion via jdk_version_id
    │
    └──→ JdkDocChunkRepository.save(chunk)
            ↑ Links to JdkVersion and JdkDocElement
            ↑ embedding field indexed by Hibernate Search
```

## Data Models

### Input (JdkVersion from index.json)

```json
{
  "version": "25.0.3",
  "major": 25,
  "minor": 0,
  "security": 3,
  "javaRuntime": "hotspot",
  "generator": "javadoc",
  "generatedAt": "2026-01-15T10:30:00Z",
  "typeCount": 12345,
  "packageCount": 234,
  "moduleCount": 12,
  "chunkCount": 50000
}
```

### Output (JdkDocChunk for semantic search)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "version": "25.0.3",
  "chunkId": "java.io.BufferedInputStream#read(byte[],int,int)",
  "kind": "METHOD",
  "qualifiedType": "java.io.BufferedInputStream",
  "packageName": "java.io",
  "moduleName": "java.base",
  "member": "read",
  "signature": "read(byte[],int,int)",
  "text": "Reads up to len bytes of data from the input stream...",
  "since": "1.1",
  "deprecated": false,
  "sourceFile": "BufferedInputStream.java",
  "sourceLine": 234,
  "part": 1,
  "parts": 1,
  "embedding": [0.123, -0.456, 0.789, ...],
  "parentChunkId": null
}
```

## Error States

- `ConstraintViolationException` — Duplicate `chunk_id` for the same `jdk_version_id` (unique constraint violation)
- `IllegalArgumentException` — Invalid version format (must match major.minor.security pattern)
- `PersistenceException` — Database connection failure during entity persistence
