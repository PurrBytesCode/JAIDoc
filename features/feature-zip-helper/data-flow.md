# Data Flow

## Overview

The ZIP helper searches through all entries in a ZIP file to find the first entry whose name matches the target filename, accounting for version-prefixed directories and Windows-style paths.

## Sequence Diagram

```
ZIPHelper.findZipEntry(zipFile, "index.json")
    │
    ├──→ zipFile.entries() — iterate all entries
    │     │
    │     ├──→ entry.getName() — "index.json"
    │     │     └── exact match ✓ → return entry
    │     │
    │     ├──→ entry.getName() — "jdk-25.0.3/index.json"
    │     │     └── endsWith("/index.json") ✓ → return entry
    │     │
    │     └──→ entry.getName() — "jdk-25.0.3\\index.json"
    │           └── replace('\\', '/') = "jdk-25.0.3/index.json"
    │               └── endsWith("/index.json") ✓ → return entry
    │
    └──→ Return null if no match found
```

## Data Models

### Input

```
ZipFile — a java.util.zip.ZipFile opened for reading
String name — the filename to find (e.g., "index.json", "chunks.jsonl", "api/java/lang/String.json")
```

### Output

```
ZipEntry — the first matching entry, or null if no entry matches
```

## Error States

- `IOException` — Propagated from `zipFile.entries()` if the ZIP file is corrupted or unreadable
- `NullPointerException` — If `zipFile` or `name` is null (caller's responsibility to validate)
