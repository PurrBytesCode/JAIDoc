---
name: feature-database-ingestion
status: implemented
date: 2026-06-21
---

# Database Ingestion

> Reads JDK documentation ZIPs and ingests them into the database: manifest parsing, structural element ingestion, chunk
> ingestion with embedding generation.

## Context

The ingestion service reads the output of the DocumentationService (a ZIP file per JDK version) and persists it to the
database. It operates in three phases:

1. **Manifest reading** — Parses `index.json` from the ZIP to create a `JdkVersion` entity with metadata (version,
   major, minor, security, type count, chunk count, etc.)
2. **Structural element ingestion** — Reads all `.json` files (except `index.json`) from the ZIP and creates
   `JdkDocElement` records for modules, packages, and types
3. **Chunk ingestion** — Reads `chunks.jsonl` from the ZIP, generates embeddings via `EmbeddingService`, links chunks to
   their parent elements, and persists them

The ingestion is idempotent — if a version already exists in the database, it's returned as-is. Errors set the status to
`FAILED`.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/service/IngestionService.java` — The ingestion service
- `src/main/java/com/purrbyte/ai/service/EmbeddingService.java` — Embedding generation
- `src/main/java/com/purrbyte/ai/util/JdkDistributionDownloader.java` — Version parsing utility
- `src/main/java/com/purrbyte/ai/util/ZIPHelper.java` — ZIP entry lookup
- `src/test/java/com/purrbyte/ai/service/IngestionSearchIntegrationTest.java` — Integration test for ingest + search

## Scope

In: Manifest parsing, structural element ingestion, chunk ingestion with embeddings, idempotent behavior, status
management (INGESTING → READY / FAILED), batch processing (200 records per flush)
Out: ZIP generation (covered in DocumentationService), vector search (covered in Semantic Search)

## Implementation

### Architecture

The service is a Spring `@Service` with constructor injection. It uses `@Transactional` on the main `ingest()` method to
ensure atomicity of the ingestion process. It consists of three private methods:

1. **`readManifestFromZip(version, zipFile)`** — Parses `index.json` from the ZIP and creates a `JdkVersion` entity with
   all metadata from the manifest
2. **`ingestElementsFromZip(jdkVersion, zipFile, totalExpected, startMillis)`** — Iterates all `.json` files in the
   ZIP (except `index.json`), parses each, and creates `JdkDocElement` records
3. **`ingestChunksFromZip(jdkVersion, zipFile, totalChunks, startMillis)`** — Reads `chunks.jsonl`, generates embeddings
   for each chunk, links to parent elements, and persists

### Files

- `src/main/java/com/purrbyte/ai/service/IngestionService.java` — The ingestion service

### Data Flow

```
IngestionService.ingest(version)
    │
    ├──→ Find ZIP: documentationService.getVersionZip(version)
    │
    ├──→ Check idempotency: jdkVersionRepository.findByVersion(version)
    │     └── If exists → return existing version
    │
    ├──→ Phase 1: Read manifest
    │     └── ZIPHelper.findZipEntry(zipFile, "index.json")
    │         └── Parse index.json → JdkVersion with metadata
    │
    ├──→ Phase 2: Ingest elements
    │     └── Iterate .json files → parse JSON → JdkDocElement
    │         └── Batch flush every 200 records
    │
    └──→ Phase 3: Ingest chunks
          └── ZIPHelper.findZipEntry(zipFile, "chunks.jsonl")
              └── Parse each JSONL line → JdkDocChunk with embedding
                  └── Link to JdkDocElement via findByJdkVersionAndQualifiedId
                  └── Batch flush every 200 records
```

### Configuration

- `src/main/resources/configurations/ingest-configuration.yml` — Ingestion enable/disable settings

## Tests

- `src/test/java/com/purrbyte/ai/service/IngestionSearchIntegrationTest.java` — Integration test: creates a test ZIP
  fixture programmatically, ingests version "25.0.3", searches for "read bytes from a stream", verifies hits returned
  for the ingested version and no hits for a non-ingested version

## Notes

- The ingestion uses `@Transactional` on the main method — if any phase fails, the entire transaction rolls back (except
  for the elements and chunks already persisted, since they're flushed every 200 records)
- The `persistElementFromJson()` method returns `true` if the element was persisted, `false` if it was skipped (e.g.,
  missing 'kind' field). This is used for progress logging.
- The `ingestChunksFromZip()` method links each chunk to its parent element via
  `jdkDocElementRepository.findByJdkVersionAndQualifiedId(jdkVersion, ownerId)`, where `ownerId` is the chunk's
  metadata "type" field (or the chunkId if the type is missing)
- The `formatDuration()` utility formats elapsed time as a human-readable string (e.g., "123ms", "5s", "2m30s")
