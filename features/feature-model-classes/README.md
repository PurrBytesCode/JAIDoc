---
name: feature-model-classes
status: implemented
date: 2026-06-21
---

# Model Classes

> Enums and DTOs that represent domain concepts used across the application.

## Context

This feature defines the core domain types that are used throughout the application: `ElementKind` categorizes Javadoc
elements (MODULE, PACKAGE, TYPE), `IngestStatus` tracks the lifecycle state of a JDK version during ingestion,
`Progress` represents progress updates for the documentation generation pipeline, and `JdkSearchResult` is the DTO
returned from semantic search queries.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/model/ElementKind.java` — Javadoc element kind enum
- `src/main/java/com/purrbyte/ai/model/IngestStatus.java` — Ingestion lifecycle state enum
- `src/main/java/com/purrbyte/ai/model/dto/Progress.java` — Pipeline progress update DTO
- `src/main/java/com/purrbyte/ai/model/dto/JdkSearchResult.java` — Semantic search result DTO

## Scope

In: Enum definitions, DTOs with their fields and meanings, progress phase constants
Out: Entity definitions (covered in feature-data-models), service logic that uses these types

## Implementation

### Architecture

The four classes form two categories:

1. **Enums** — `ElementKind` and `IngestStatus` are used as enum types in JPA entities (`@Enumerated(EnumType.STRING)`)
2. **DTOs** — `Progress` and `JdkSearchResult` are plain Java objects used for data transfer between services

### Files

- `src/main/java/com/purrbyte/ai/model/ElementKind.java` — MODULE, PACKAGE, TYPE
- `src/main/java/com/purrbyte/ai/model/IngestStatus.java` — INGESTING, READY, FAILED
- `src/main/java/com/purrbyte/ai/model/dto/Progress.java` — Progress with percentage and phase
- `src/main/java/com/purrbyte/ai/model/dto/JdkSearchResult.java` — Search result DTO

### Data Flow

```
ElementKind        ─→ JdkDocElement.kind (ENUMERATED)
IngestStatus       ─→ JdkVersion.status (ENUMERATED)
Progress           ─→ DocumentationService → Consumer<Progress>
JdkSearchResult    ─→ JdkSearchService.search() → JavaDocMCP.searchJavadoc()
```

### Configuration

None — these are pure domain types with no configuration.

## Tests

No dedicated unit tests exist for these model classes. They are implicitly tested through the entity and service tests
that use them.

## Notes

- `IngestStatus.INGESTING` is the ambiguous state — it means either "currently being ingested" or "ingestion failed".
  The `FAILED` state is not currently used by the codebase (the `IngestionService` sets `status` to `READY` on success,
  but doesn't have a `FAILED` path), so `INGESTING` effectively means "not yet ready".
- `Progress.percentage` is rounded to 2 decimal places using `BigDecimal.setScale(2, RoundingMode.HALF_UP)` to avoid
  floating-point precision issues in progress reporting.
- `JdkSearchResult` is declared `final` — it's an immutable DTO used as a return type for search results.
