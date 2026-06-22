# Notes

## Observations

- The `DocTreeJson` handles 25+ DocTree node types — this is the most complex piece, handling everything from simple
  text nodes to complex link trees and block tags
- The `normalize()` method is critical for producing clean text for embeddings — it collapses repeated spaces while
  preserving significant newlines
- The `bestBreak()` method prefers paragraph boundaries over line breaks over sentence boundaries over hard cuts — this
  ensures semantic coherence of chunks

## Decisions

- Use Jackson 3 (`tools.jackson.*`) instead of Jackson 2 (`com.fasterxml.jackson.*`) — Jackson 3 requires Java 17+, but
  the application already runs on Java 17+
- The `ChunkWriter.split()` method uses a sliding window approach with overlap — this ensures chunks have some context
  overlap at boundaries, which is important for embedding quality
- The `DocTreeJson` serializes both structured and plain text representations of Javadoc comments — the structured
  representation is useful for debugging and the plain text is used for embeddings

## Open Questions

- Should we add a `--min-chunk-size` option to filter out very small chunks that don't have enough context for
  meaningful embeddings?
- The `parseVersion()` method in `JdkDistributionDownloader` is duplicated in this codebase — should it be moved to a
  shared utility?
