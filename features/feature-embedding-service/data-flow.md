# Data Flow

## Overview

The embedding service is a thin wrapper that adds the "passage: " / "query: " prefixes to texts before delegating to the Spring AI EmbeddingModel. It's used during indexing (passage) and search (query).

## Sequence Diagram

```
EmbeddingService.embedPassage(text)
    │
    └──→ EmbeddingModel.embed("passage: " + text)
            └──→ float[384] — vector embedding

EmbeddingService.embedQuery(query)
    │
    └──→ EmbeddingModel.embed("query: " + query)
            └──→ float[384] — vector embedding
```

## Data Models

### Input

```
String text — the text to embed (will be prefixed with "passage: " or "query: ")
```

### Output

```
float[384] — 384-dimensional vector embedding from the multilingual-e5 model
```

## Error States

- `IllegalArgumentException` — If the text is null (Spring AI model validation)
- `OutOfMemoryError` — If the JVM cannot allocate memory for the embedding model
