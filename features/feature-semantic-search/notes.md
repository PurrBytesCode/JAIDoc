# Notes

## Observations

- The search is very simple — just one method — but it's the core of the MCP tool that AI models use to query documentation.
- The version filter is critical — without it, the search would return chunks from all versions, mixing results from different JDK APIs.

## Decisions

- The `toResult()` method retrieves the raw JSON from the parent `JdkDocElement` — this allows the MCP tool to return the structured Javadoc alongside the text, giving AI models access to both the searchable text and the structured metadata.
- The search uses Hibernate Search's kNN feature with cosine similarity — the `@VectorField(dimension = 384, vectorSimilarity = VectorSimilarity.COSINE)` annotation on `JdkDocChunk.embedding` configures this.

## Open Questions

- Should we add pagination to the search results? Currently the topK parameter is the only way to limit results.
- Should we add result ranking beyond cosine similarity (e.g., boosting by element kind — methods over types)?
