# Data Flow

## Overview

The auto-discovery service walks the data directory on startup, finds ZIP files for JDK documentation, and triggers
ingestion for any version that hasn't been processed yet. Each version is processed independently — errors don't block
others.

## Sequence Diagram

```
ApplicationReadyEvent
    │
    ├──→ ingest.scan.auto == true?
    │     └── If false → skip
    │
    ├──→ Files.walk(dataDirectory)
    │     └── Filter: regular files ending with ".zip"
    │         └── Extract version from filename
    │             └── Process version:
    │                 ├──→ findByVersion(version)
    │                 │   └── If exists + READY → skip
    │                 │   └── If exists + not READY → re-ingest
    │                 │   └── If not exists → ingest
    │                 └── Catch all exceptions → log error, continue
```

## Data Models

### Input (ZIP files in data directory)

```
data/
├── 25.0.3.zip
├── 21.0.11.zip
└── 17.0.13.zip
```

### Output (per version)

```
Discovered ZIP for version 25.0.3 at data/25.0.3.zip
Version 25.0.3 not found in database, triggering ingestion
Successfully ingested version 25.0.3 (status=READY, chunks=50000)
```

## Error States

- `IOException` — Failed to list the data directory
- `IOException` — Ingestion failed for a specific version (logged, doesn't block others)
- `Exception` — Unexpected error processing a version (logged, doesn't block others)
