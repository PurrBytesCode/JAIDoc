# Notes

## Observations

- The `JdkDocChunk` entity is the most complex — it has 20+ fields, Hibernate Search annotations, and the embedding
  vector field
- The denormalized `version` field on `JdkDocChunk` is a deliberate optimization to avoid JOINs in kNN queries. It must
  be kept in sync with `JdkVersion.version` during ingestion.
- The unique constraint on (`jdk_version_id`, `chunk_id`) prevents duplicate chunks but allows the same `chunkId` across
  different versions (e.g., `BufferedInputStream#read` exists in both JDK 25 and JDK 21)

## Decisions

- UUID primary keys — avoids sequential ID generation and makes IDs hard to guess
- `FetchType.LAZY` on all `@ManyToOne` relationships — prevents unnecessary JOINs in queries that only access the parent
  entity
- `CascadeType.ALL` only on `JdkVersion` side — when you delete a version, all its elements and chunks are deleted too.
  But deleting a chunk doesn't cascade up.
- `@Enumerated(EnumType.STRING)` on both `IngestStatus` and `ElementKind` — stores the enum name as a string, not an
  ordinal, so the database doesn't break if the enum order changes

## Open Questions

- Should we add a `lastAccessedAt` field to `JdkVersion` for tracking which versions are actively used?
- The `parentChunkId` field on `JdkDocChunk` is currently unused — it was planned for reassembling split chunks but the
  current implementation doesn't use it.
