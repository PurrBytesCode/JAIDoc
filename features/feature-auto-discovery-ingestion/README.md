---
name: feature-auto-discovery-ingestion
status: implemented
date: 2026-06-21
---

# Auto-Discovery Ingestion

> Auto-discovers JDK documentation ZIP files in the data directory on startup and triggers ingestion for any version
> that hasn't been processed yet.

## Context

When the application starts, the auto-discovery service walks the configured data directory (recursively) looking for
`*.zip` files. For each ZIP found, it extracts the version from the filename (stripping `.zip`) and checks the database:

- If the version exists with status `READY`, it's skipped
- If the version exists with any other status, re-ingestion is triggered
- If the version doesn't exist in the database, ingestion is triggered

Errors during ingestion of one version do NOT block processing of other versions — each version is processed
independently.

The service is conditionally enabled via
`@ConditionalOnProperty(prefix = "ingest", name = "enabled", havingValue = "true")`.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/service/IngestDiscoveryService.java` — The auto-discovery service
- `src/main/java/com/purrbyte/ai/service/IngestionService.java` — Ingestion trigger
- `src/main/java/com/purrbyte/ai/repository/JdkVersionRepository.java` — Version lookup

## Scope

In: ZIP file discovery, version extraction from filename, database status checking, per-version error isolation,
conditional activation
Out: The actual ingestion logic (covered in Database Ingestion), ZIP generation (covered in DocumentationService)

## Implementation

### Architecture

The service is a Spring `@Service` activated by the `ApplicationReadyEvent`. It uses `@ConditionalOnProperty` to disable
itself if `ingest.enabled=false`. It walks the data directory with `Files.walk()` and processes each ZIP file
independently.

### Files

- `src/main/java/com/purrbyte/ai/service/IngestDiscoveryService.java` — The auto-discovery service

### Data Flow

```
ApplicationReadyEvent
    │
    ├──→ Check: ingest.scan.auto == true?
    │     └── If false → skip
    │
    ├──→ Check: dataDirectory exists?
    │     └── If not → log warning and skip
    │
    ├──→ Files.walk(dataDirectory) — recursive walk
    │     └── Filter: regular files ending with ".zip"
    │         └── Extract version from filename (strip ".zip")
    │             └── Process version:
    │                 ├──→ findByVersion(version)
    │                 │   └── If exists + READY → skip
    │                 │   └── If exists + not READY → re-ingest
    │                 │   └── If not exists → ingest
    │                 └── Catch all exceptions — one version's failure doesn't block others
```

### Configuration

- `src/main/resources/configurations/ingest-configuration.yml` — `ingest.enabled`, `ingest.scan.auto`, `data.directory`

## Tests

No dedicated unit tests exist for the Auto-Discovery Ingestion service. It is implicitly tested through the integration
tests that exercise the full ingestion pipeline.

## Notes

- The `@ConditionalOnProperty(prefix = "ingest", name = "enabled", havingValue = "true")` annotation means the service
  won't be created if `ingest.enabled=false` — this allows disabling ingestion entirely
- The `ingest.scan.auto` flag controls whether auto-scan happens on startup — if false, the service still exists but
  doesn't do anything
- The version extraction from filename is simple: `fileName.substring(0, fileName.length() - 4)` — this assumes the
  filename is `<version>.zip` with no other extensions
