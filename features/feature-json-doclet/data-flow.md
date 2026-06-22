# Data Flow

## Overview

The JSON Doclet processes JDK source code through a pipeline: it iterates modules, packages, and types, serializes each
to JSON, extracts Javadoc comments in both structured and plain text formats, and writes text chunks for semantic
search. The output includes per-element JSON files, a manifest, and a line-delimited JSON file of chunks.

## Sequence Diagram

```
JsonDoclet.run(env)
    │
    ├──→ Build module JSON → write module-<name>.json
    │     └── DocTreeJson.comment() → structured comment
    │     └── DocTreeJson.fullText() → plain text for embeddings
    │     └── ChunkWriter.write(id, text, metadata, documented)
    │
    ├──→ Build package JSON → write api/<pkg>/package-info.json
    │     └── Same pattern: comment + fullText + chunk
    │
    ├──→ Build type JSON → write api/<pkg>/<Type>.json
    │     └── For each member: buildField()/buildExecutable()
    │          └── DocTreeJson.comment() + fullText() + ChunkWriter.write()
    │
    └──→ Write index.json (manifest)
```

## Data Models

### Input (CLI Options)

```
-d <dir>                    Output directory (default: json-doclet-out)
--doc-version <version>     Version recorded in index.json
--pretty                    Format JSON with indentation
--no-chunks                 Do not generate chunks.jsonl
--chunks-file <path>        Path of the JSONL chunks file
--max-chunk-chars <n>       Maximum chunk size (default: 4000)
--chunk-overlap <n>         Overlap between fragments (default: 200)
--only-documented           Emit chunks only for documented elements
```

### Output (JsonDoclet)

```
json-doclet-out/
├── index.json              Manifest with modules, packages, types, counts
├── module-java.base.json   Module documentation
├── api/
│   └── java/io/
│       ├── package-info.json
│       ├── BufferedInputStream.json
│       └── FileInputStream.json
└── chunks.jsonl            Line-delimited JSON for semantic search
```

### Output (Chunk)

```json
{
  "id": "java.io.BufferedInputStream#read(byte[],int,int)",
  "text": "Reads up to len bytes of data from this input stream. An attempt to read after EOF will return -1.",
  "metadata": {
    "kind": "METHOD",
    "documented": true,
    "type": "java.io.BufferedInputStream",
    "package": "java.io",
    "module": "java.base",
    "member": "read",
    "signature": "read(byte[],int,int)",
    "since": "1.1",
    "file": "BufferedInputStream.java",
    "line": 234
  }
}
```

## Error States

- `Exception` — Any error during JSON serialization or file writing causes the doclet to fail and return `false`
- `IOException` — File I/O errors (directory creation, file writing)
- `IllegalArgumentException` — Invalid CLI option values (e.g., `--max-chunk-chars` not a valid integer)
