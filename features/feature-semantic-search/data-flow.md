# Data Flow

## Overview

The semantic search service performs kNN vector search over JDK documentation chunks. It embeds the user's query, finds the top-K most similar chunks using cosine similarity, filters by version, and returns results with metadata and raw JSON.

## Sequence Diagram

```
JavaDocMCP.searchJavadoc(version, query, topK=10)
    │
    └──→ JdkSearchService.search(version, query, topK)
            ├──→ EmbeddingService.embedQuery("query: " + query) → float[384]
            ├──→ SearchSession.search(JdkDocChunk.class)
            │   └──→ .where(f → f.knn(topK).field("embedding").matching(queryVector)
            │             .filter(f.match().field("version").matching(version)))
            │         └──→ .select(f → f.composite(f.entity(), f.score()).as(toResult))
            │             └──→ fetchHits(topK)
            └──→ toResult(chunk, score) → JdkSearchResult
                    └── Includes: chunkId, kind, qualifiedType, member, signature, text, score, rawJson
```

## Data Models

### Input

```json
{
  "version": "25.0.3",
  "query": "read bytes from a stream",
  "topK": 10
}
```

### Output

```json
[
  {
    "chunkId": "java.io.BufferedInputStream#read(byte[],int,int)",
    "kind": "METHOD",
    "qualifiedType": "java.io.BufferedInputStream",
    "member": "read",
    "signature": "read(byte[],int,int)",
    "text": "Reads up to len bytes of data from this input stream. An attempt to read after EOF will return -1.",
    "score": 0.847,
    "rawJson": "{\"type\": \"class\", ...}"
  }
]
```

## Error States

- `IllegalArgumentException` — If the query is null (Spring AI model validation)
- `IllegalArgumentException` — If `topK` is <= 0 (Hibernate Search validation)
