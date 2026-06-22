---
name: feature-data-models
status: implemented
date: 2026-06-21
---

# Data Models

> JPA entities that form the persistence layer for JDK documentation storage and semantic search indexing.

## Context

This feature defines the three JPA entities that store all JDK documentation data in SQLite. The entities form a hierarchical structure: `JdkVersion` (top-level, tracks a single JDK version), `JdkDocElement` (structural elements — modules, packages, types), and `JdkDocChunk` (text chunks for semantic search, indexed with Hibernate Search). The `JdkDocChunk` entity is the most complex, with Hibernate Search annotations for full-text search and kNN vector search on 384-dimensional embeddings.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/domain/JdkVersion.java` — JdkVersion entity
- `src/main/java/com/purrbyte/ai/domain/JdkDocElement.java` — JdkDocElement entity
- `src/main/java/com/purrbyte/ai/domain/JdkDocChunk.java` — JdkDocChunk entity
- `src/main/java/com/purrbyte/ai/model/converter/FloatArrayConverter.java` — Converter for float[] embeddings → byte[] BLOB
- `src/test/java/com/purrbyte/ai/model/converter/FloatArrayConverterTest.java` — Tests for the converter

## Scope

In: JPA entity definitions, entity relationships (ManyToOne, OneToMany), Hibernate Search annotations, UUID primary key strategy, unique constraints, denormalized search fields, embedding converter
Out: Repository interfaces (covered in feature-repository-layer), service logic that uses these entities (covered in individual service features)

## Implementation

### Architecture

The entities form a strict hierarchy: `JdkVersion` → `JdkDocElement` → `JdkDocChunk`. A `JdkVersion` has many elements (modules, packages, types) and many chunks. An element has many chunks (one chunk per method/field/constructor). A chunk belongs to exactly one element and one version.

The hierarchy is:
```
JdkVersion (1) ───┬─── (M) JdkDocElement
                  └─── (M) JdkDocChunk
JdkDocElement (1) ─── (M) JdkDocChunk
```

Each entity uses `GenerationType.UUID` for primary keys, `@ManyToOne(fetch = FetchType.LAZY)` for relationships, and `CascadeType.ALL` on the `JdkVersion` side to cascade persistence through the hierarchy.

### Files

- `src/main/java/com/purrbyte/ai/domain/JdkVersion.java` — Top-level entity tracking a JDK version with metadata and counts
- `src/main/java/com/purrbyte/ai/domain/JdkDocElement.java` — Structural Javadoc elements (modules, packages, types)
- `src/main/java/com/purrbyte/ai/domain/JdkDocChunk.java` — Text chunks for semantic search, indexed with Hibernate Search
- `src/main/java/com/purrbyte/ai/model/converter/FloatArrayConverter.java` — JPA AttributeConverter for float[] ↔ byte[]

### Data Flow

```
JdkVersion → JdkDocElement (ManyToOne) → JdkDocChunk (ManyToOne)
    ↑              ↑                        ↑
    │              │                        │
  version        kind                     embedding (384-dim float[])
  metadata       qualifiedId              @VectorField (cosine)
  counts         packageName              @FullTextField (text)
  status         moduleName               @KeywordField (metadata)
```

### Configuration

- `src/main/resources/configurations/db-configuration.yml` — SQLite datasource, Hibernate Search index directory
- `src/main/java/com/purrbyte/ai/model/converter/FloatArrayConverter.java` — The converter is annotated with `@Converter` (auto-discovered by JPA)

## Tests

- `src/test/java/com/purrbyte/ai/model/converter/FloatArrayConverterTest.java` — Unit tests for the embedding converter (4 test cases: round-trip, null handling, empty array, full 384-dimension)

## Notes

- The `JdkDocChunk` entity has a unique constraint on (`jdk_version_id`, `chunk_id`) to prevent duplicate chunks per version
- The `version` field on `JdkDocChunk` is denormalized — it's the same as `JdkVersion.version` but stored directly on the chunk so that kNN queries can filter by version without a JOIN. The `JdkSearchService.search()` method uses `f.match().field("version").matching(version)` to filter chunks.
- The `@VectorField(dimension = 384, vectorSimilarity = VectorSimilarity.COSINE)` annotation on the embedding field configures Hibernate Search's vector search. The 384 dimension matches the multilingual-e5 model output.
- The `FloatArrayConverter` is required because SQLite has no native vector column type. The converter serializes `float[]` to `byte[]` via `ByteBuffer/FloatBuffer`, storing it as a BLOB.
- All entities use Lombok annotations (`@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`) for boilerplate reduction.
- The `JdkVersion.status` field uses `IngestStatus.INGESTING` as the default (via `@Builder.Default`) — it transitions to `READY` or `FAILED` after ingestion completes.
