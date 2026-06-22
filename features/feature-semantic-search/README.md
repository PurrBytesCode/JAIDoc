---
name: feature-semantic-search
status: implemented
date: 2026-06-21
---

# Semantic Search

> Vector kNN search over JDK documentation chunks using Hibernate Search and the multilingual-e5 embedding model.

## Context

The semantic search service performs k-nearest-neighbors search on `JdkDocChunk` entities using 384-dimensional vector embeddings. It embeds the user's query with the "query: " prefix (matching the "passage: " prefix used during indexing), then uses Hibernate Search's kNN feature to find the top-K most similar chunks. The search is filtered by version — only chunks from one JDK version at a time are returned.

The search result includes the chunk text, metadata (kind, qualified type, member, signature), a cosine similarity score, and the raw JSON representation of the element's Javadoc structure.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/service/JdkSearchService.java` — The search service
- `src/main/java/com/purrbyte/ai/service/EmbeddingService.java` — Query embedding generation
- `src/main/java/com/purrbyte/ai/model/dto/JdkSearchResult.java` — Search result DTO

## Scope

In: kNN vector search, version filtering, result construction with raw JSON
Out: Query parsing/understanding (covered in Embedding Service), chunk generation (covered in JSON Doclet), database ingestion (covered in Database Ingestion)

## Implementation

### Architecture

The service is a Spring `@Service` with constructor injection. It has a single method `search(version, query, topK)` that:

1. Embeds the query with "query: " prefix
2. Uses Hibernate Search's kNN feature to find the top-K similar chunks
3. Filters by version using `f.match().field("version").matching(version)`
4. Constructs `JdkSearchResult` DTOs with chunk metadata and raw JSON

### Files

- `src/main/java/com/purrbyte/ai/service/JdkSearchService.java` — The search service

### Data Flow

```
JavaDocMCP.searchJavadoc(version, query, topK=10)
    │
    └──→ JdkSearchService.search(version, query, topK)
            ├──→ EmbeddingService.embedQuery("query: " + query) → float[384]
            ├──→ SearchSession.search(JdkDocChunk.class)
            │   └──→ .where(f → f.knn(topK).field("embedding").matching(queryVector)
            │             .filter(f.match().field("version").matching(version)))
            │         └──→ fetchHits(topK)
            └──→ toResult(chunk, score) → JdkSearchResult
                    └── Includes: chunkId, kind, qualifiedType, member, signature, text, score, rawJson
```

### Configuration

- `src/main/resources/configurations/ai-configuration.yml` — ONNX model URI (same model used for indexing)

## Tests

No dedicated unit tests exist for the Semantic Search service. It is implicitly tested through the integration tests that exercise the full search pipeline.

## Notes

- The `@VectorField(dimension = 384, vectorSimilarity = VectorSimilarity.COSINE)` annotation on `JdkDocChunk.embedding` configures Hibernate Search's vector search with cosine similarity
- The version filter is critical — without it, the search would return chunks from all versions, mixing results from different JDK APIs
- The `toResult()` method retrieves the raw JSON from the parent `JdkDocElement` via `c.getJDKDocElement().getRawJson()` — this allows the MCP tool to return the structured Javadoc alongside the text
