# Notes

## Observations

- The ingestion is idempotent — if a version already exists in the database, it's returned as-is. This means the ingestion can be safely retried.
- The `ingestChunksFromZip()` method generates embeddings for each chunk on the fly — this is the bottleneck of the ingestion process (384-dimensional vector generation for potentially 50,000+ chunks).
- The batch flush every 200 records is important for memory efficiency — without it, Hibernate would accumulate all entities in the persistence context.

## Decisions

- The `@Transactional` annotation on the main `ingest()` method ensures atomicity — if any phase fails, the entire transaction rolls back. However, elements and chunks are flushed every 200 records, which means partial data may be persisted even if ingestion fails mid-way.
- The `formatDuration()` utility formats elapsed time as a human-readable string — this is used for progress logging throughout the ingestion process.

## Open Questions

- Should we add a checkpoint mechanism so that partial ingestions can be resumed rather than re-ingested from scratch?
- The embedding generation is synchronous — should we consider batch embedding generation with a separate thread pool for better performance?
