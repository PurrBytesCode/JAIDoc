---
name: feature-embedding-service
status: implemented
date: 2026-06-21
---

# Embedding Service

> Thin wrapper over the Spring AI transformer EmbeddingModel. Adds the "passage: " / "query: " prefixes required by the multilingual-e5 model family to produce comparable embeddings.

## Context

The embedding service is a minimal wrapper around Spring AI's `EmbeddingModel`. The multilingual-e5 model family (which JAIDoc uses) requires specific prefixes — "passage: " for texts being indexed and "query: " for search queries — to produce embeddings in the same vector space. Without these prefixes, the embeddings are incomparable and semantic search quality degrades silently.

The service provides two methods: `embedPassage()` for indexing texts and `embedQuery()` for search queries. Both delegate to the Spring AI model after adding the appropriate prefix.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/service/EmbeddingService.java` — The embedding service wrapper
- `src/main/java/com/purrbyte/ai/domain/JdkDocChunk.java` — Entity that stores embeddings (for context)

## Scope

In: Passage/query prefix convention, embedding generation delegation
Out: The embedding model itself (covered in feature-configuration), chunk splitting and writing (covered in feature-json-doclet), vector search indexing (covered in feature-data-models)

## Implementation

### Architecture

The service is a Spring `@Service` with constructor injection (`@RequiredArgsConstructor`). It has no business logic — just prefixing and delegation. The `EmbeddingModel` is injected by Spring from the configuration in `ai-configuration.yml`, which configures the ONNX transformer model.

The model produces 384-dimensional embeddings (matching the `@VectorField(dimension = 384)` annotation on `JdkDocChunk.embedding`).

### Files

- `src/main/java/com/purrbyte/ai/service/EmbeddingService.java` — The embedding service wrapper

### Data Flow

```
JdkSearchService.search(version, query)
    │
    └──→ EmbeddingService.embedQuery("query: " + query)
            └──→ Spring AI EmbeddingModel.embed("query: " + query)
                    └──→ float[384] — vector embedding

IngestionService.ingestChunks(jdkVersion, chunks)
    │
    └──→ EmbeddingService.embedPassage("passage: " + chunkText)
            └──→ Spring AI EmbeddingModel.embed("passage: " + chunkText)
                    └──→ float[384] — vector embedding for the chunk
```

### Configuration

- `src/main/resources/configurations/ai-configuration.yml` — ONNX model URI and tokenizer JSON path

## Tests

No dedicated unit tests exist for the EmbeddingService. It is implicitly tested through the integration tests that exercise the full ingestion and search pipeline.

## Notes

- The prefixes are critical — omitting them silently degrades ranking quality, which is hard to debug
- The 384 dimension matches the multilingual-e5 model output and the `@VectorField(dimension = 384)` annotation on `JdkDocChunk.embedding`
- The service is intentionally minimal — all business logic (chunk splitting, batch processing) is in other services
