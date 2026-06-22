---
name: feature-repository-layer
status: implemented
date: 2026-06-21
---

# Repository Layer

> JPA repositories for CRUD operations and custom queries on JDK documentation entities.

## Context

This feature defines three Spring Data JPA repositories that provide data access for the JAIDoc entities:

1. **JdkVersionRepository** — CRUD for `JdkVersion` plus a custom query to list available versions (READY status, ordered by major/minor/security descending)
2. **JdkDocElementRepository** — CRUD for `JdkDocElement` plus a lookup by version and qualified ID (used during chunk ingestion to link chunks to their parent elements)
3. **JdkDocChunkRepository** — Basic CRUD only (no custom queries)

The repositories extend `JpaRepository` and use Spring Data JPA's derived query methods (`findByVersion`, `findByJdkVersionAndQualifiedId`) and JPQL queries.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/repository/JdkVersionRepository.java` — Version repository
- `src/main/java/com/purrbyte/ai/repository/JdkDocElementRepository.java` — Element repository
- `src/main/java/com/purrbyte/ai/repository/JdkDocChunkRepository.java` — Chunk repository

## Scope

In: Repository definitions, custom queries, derived query methods
Out: Entity definitions (covered in Data Models), service logic that uses these repositories

## Implementation

### Architecture

All three repositories extend `JpaRepository`, which provides basic CRUD operations (save, findById, findAll, delete, etc.) automatically. Custom queries are added via `@Query` annotations or Spring Data derived method names.

### Files

- `src/main/java/com/purrbyte/ai/repository/JdkVersionRepository.java` — Version repository
- `src/main/java/com/purrbyte/ai/repository/JdkDocElementRepository.java` — Element repository
- `src/main/java/com/purrbyte/ai/repository/JdkDocChunkRepository.java` — Chunk repository

### Data Flow

```
JdkVersionRepository.findAllVersionStringsOrderByMajorDesc()
    │
    └──→ JPQL: SELECT v.version FROM JdkVersion v
                 WHERE v.status = 'READY'
                 ORDER BY v.major DESC, v.minor DESC, v.security DESC
            └──→ List<String> — version strings

JdkDocElementRepository.findByJdkVersionAndQualifiedId(jdkVersion, ownerId)
    │
    └──→ Derived query: findByJdkVersionAndQualifiedId
            └──→ Optional<JdkDocElement> — the parent element for a chunk
```

### Configuration

None — the repositories are discovered automatically by Spring Data JPA.

## Tests

No dedicated unit tests exist for the repository layer. The custom query is implicitly tested through the integration tests that exercise the search pipeline.

## Notes

- The `JdkDocChunkRepository` has no custom queries — it's just a basic CRUD repository. All chunk operations (save, find by ID) are handled by Spring Data JPA's default methods.
- The `JdkVersionRepository.findAllVersionStringsOrderByMajorDesc()` query uses JPQL (not native SQL) and orders by the denormalized `major`, `minor`, and `security` fields — this is more efficient than ordering by the `version` string.
