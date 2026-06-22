---
name: feature-json-doclet
status: implemented
date: 2026-06-21
---

# JSON Doclet

> Custom Javadoc doclet that serializes the complete Javadoc to JSON, including structured comments and text chunks for
> semantic search.

## Context

The doclet is the core engine of JAIDoc. It runs as a `javax.tool.JavaCompiler` doclet on JDK source code and produces
structured JSON output: one JSON file per top-level type (`api/<package>/<Type>.json`), one per module (
`module-<name>.json`), a manifest (`index.json`), and a line-delimited JSON file for semantic search (`chunks.jsonl`).
It uses the modern `jdk.javadoc.doclet` API (JDK 9+) and Jackson 3 (`tools.jackson.*`) for JSON serialization.

The doclet produces two kinds of output:

1. **Structured JSON** — one file per element, with full Javadoc comment serialization (both structured and plain text)
2. **Chunks** — flat text chunks with metadata, ready to be embedded and indexed for semantic search

## Feature Inputs

- `src/main/java/com/purrbyte/ai/doclet/JsonDoclet.java` — Main doclet, CLI options, orchestration
- `src/main/java/com/purrbyte/ai/doclet/TypeJsonBuilder.java` — Converts language model elements to JSON and emits
  chunks
- `src/main/java/com/purrbyte/ai/doclet/DocTreeJson.java` — Serializes Javadoc comment trees to structured JSON + plain
  text
- `src/main/java/com/purrbyte/ai/doclet/ChunkWriter.java` — Writes chunks.jsonl with smart text splitting

## Scope

In: Javadoc-to-JSON serialization, chunk generation and splitting, CLI options, structured/plain text comment
serialization, chunk ID generation
Out: JDK source extraction (covered in DocumentationService), embedding generation (covered in EmbeddingService),
database ingestion (covered in Database Ingestion)

## Implementation

### Architecture

The doclet consists of four tightly coupled components:

1. **JsonDoclet** — The entry point. Implements `javax.javadoc.doclet.Doclet`, parses CLI options, orchestrates the
   pipeline: creates the `TypeJsonBuilder`, iterates modules/packages/types, writes JSON files, builds the index.

2. **TypeJsonBuilder** — Converts `javax.lang.model` elements (modules, packages, types, fields, methods, constructors)
   to `ObjectNode` JSON trees. Uses `DocTreeJson` to extract Javadoc comments and `ChunkWriter` to emit chunks. Handles
   record components, sealed classes, type parameters, annotations, deprecation, source locations.

3. **DocTreeJson** — Serializes Javadoc comment trees (`com.sun.source.doctree`) into two representations:
    - **Structured**: Each tree node as a JSON object with `kind` and fields (LinkTree, StartElementTree, block tags,
      etc.)
    - **Plain text**: Human-readable representation for embeddings (body text + block tags combined)
    - Forward compatible with JDK 18+ nodes (SNIPPET, SPEC, Markdown) via generic fallback

4. **ChunkWriter** — Writes `chunks.jsonl` with smart text splitting. If text exceeds `maxChars` (default 4000), splits
   at paragraph > line > sentence > hard cut boundaries. Each fragment keeps the same id with a `#part/total` suffix and
   metadata `part`/`parts` for reassembly.

### Files

- `src/main/java/com/purrbyte/ai/doclet/JsonDoclet.java` — Main doclet, CLI options, orchestration
- `src/main/java/com/purrbyte/ai/doclet/TypeJsonBuilder.java` — Converts elements to JSON and emits chunks
- `src/main/java/com/purrbyte/ai/doclet/DocTreeJson.java` — Serializes Javadoc comment trees
- `src/main/java/com/purrbyte/ai/doclet/ChunkWriter.java` — Writes chunks.jsonl with text splitting

### Data Flow

```
JsonDoclet.run()
    │
    ├──→ Iterate modules → buildModule() → module-<name>.json
    │     └── emitChunk(module, "module:...", metadata)
    │
    ├──→ Iterate packages → buildPackage() → api/<pkg>/package-info.json
    │     └── emitChunk(pkg, "package:...", metadata)
    │
    ├──→ Iterate types → buildType() → api/<pkg>/<Type>.json
    │     └── emitChunk(type, typeHeader, metadata)
    │          └── For each member: buildField()/buildExecutable()
    │               └── emitChunk(member, signature, metadata)
    │
    ├──→ Write index.json (manifest)
    │
    └──→ Close ChunkWriter → flush chunks.jsonl
```

### Configuration

- `src/main/resources/configurations/db-configuration.yml` — `doclet.work.directory`, `doclet.jar.directory`,
  `doclet.javadoc.home`, `doclet.modules`

## Tests

- `src/test/java/com/purrbyte/ai/doclet/ChunkWriterTest.java` — 10 test cases for text splitting (short text, long text
  with paragraphs, long text with lines, text without spaces, documented-only mode)
- `src/test/java/com/purrbyte/ai/doclet/DocTreeJsonTest.java` — Tests for `normalize()` (26 tests) and
  `decodeEntity()` (12 tests)

## Notes

- The doclet uses `SourceVersion.latestSupported()` to support the latest JDK version it runs on (21, 25, 27...)
- The `--only-documented` flag skips chunks for undocumented elements — useful for reducing embedding index size
- The `bestBreak()` method prefers paragraph boundaries (`\n\n`) over line breaks, over sentence boundaries, and finally
  hard cuts — this ensures semantic coherence of chunks
- The `DocTreeJson` handles 25+ DocTree node types, including JDK 18+ nodes (SNIPPET, SPEC) via a generic fallback that
  serializes `t.toString()`
- The `normalize()` method collapses repeated spaces while preserving significant newlines — this is critical for
  producing clean text for embeddings
- The `decodeEntity()` method handles XML entity references in Javadoc: `amp`, `lt`, `gt`, `quot`, `apos`, `nbsp`,
  `copy`, `reg`, `trade`, `hellip`, `mdash`, `ndash`, plus hex and decimal numeric entities
