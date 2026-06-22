# Data Flow

## Overview

The ingestion service reads JDK documentation ZIPs and persists them to the database in three phases: manifest parsing, structural element ingestion, and chunk ingestion with embedding generation. The process is idempotent and reports progress throughout.

## Sequence Diagram

```
IngestionService.ingest(version)
    │
    ├──→ Find ZIP: getVersionZip(version)
    │     └── Return null if not found → exception
    │
    ├──→ Idempotency check: findByVersion(version)
    │     └── If exists → return existing version
    │
    ├──→ Phase 1: Read manifest (index.json)
    │     └── Parse: version, major, minor, security, typeCount, chunkCount, packageCount, moduleCount
    │         └── Set status = READY, ingestedAt = now
    │
    ├──→ Phase 2: Ingest elements (all .json files except index.json)
    │     └── Parse: kind, name, qualifiedName, package, module
    │         └── Create JdkDocElement with kind, qualifiedId, simpleName, packageName, moduleName
    │         └── Batch flush every 200 records
    │
    └──→ Phase 3: Ingest chunks (chunks.jsonl)
          └── Parse: id, text, metadata (kind, type, package, module, member, signature, since, deprecated, file, line)
              └── Generate embedding: embedPassage(text) → float[384]
              └── Link to JdkDocElement via findByJdkVersionAndQualifiedId
              └── Batch flush every 200 records
```

## Data Models

### Input (index.json from ZIP)

```json
{
  "version": "25.0.3",
  "generator": "json-doclet 1.0.0",
  "generatedAt": "2026-01-15T10:30:00Z",
  "javaRuntime": "hotspot",
  "typeCount": 12345,
  "packageCount": 234,
  "moduleCount": 12,
  "chunkCount": 50000,
  "modules": [...],
  "packages": [...],
  "types": [...]
}
```

### Output (JdkVersion)

```json
{
  "version": "25.0.3",
  "major": 25,
  "minor": 0,
  "security": 3,
  "javaRuntime": "hotspot",
  "generator": "json-doclet 1.0.0",
  "generatedAt": "2026-01-15T10:30:00Z",
  "typeCount": 12345,
  "packageCount": 234,
  "moduleCount": 12,
  "chunkCount": 50000,
  "ingestedAt": "2026-06-21T14:00:00Z",
  "status": "READY"
}
```

## Error States

- `IOException("No generated documentation found for version {version}")` — ZIP not found
- `IOException("index.json not found in ZIP for version {version}")` — Invalid ZIP
- `RuntimeException` — Any parsing error during element or chunk ingestion
- `IOException` — I/O errors during ZIP reading
