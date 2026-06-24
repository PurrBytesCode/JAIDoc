# Data Flow

## Overview

The Documentation Service orchestrates the entire JDK documentation generation pipeline. It acquires the JDK source (
local or downloaded), extracts it, runs the JsonDoclet via the javadoc command, compresses the output, and reports
progress through three phases.

## Sequence Diagram

```
DocumentationService.generateJdkDocumentation(version, progress)
    │
    ├──→ validateRequest(version, major) — JDK 11+ modular, javadoc 17+
    │
    ├──→ Source acquisition:
    │     ├──→ localSrcZip() if version == running JDK
    │     └──→ downloadDistribution(version, progress) → MODULE_DOWNLOAD
    │          └──→ extractSrcZipFromArchive(archive, version)
    │
    ├──→ extractSourceZip(sourceZip, version, extractProgress) — MODULE_EXTRACT
    │     └── ZIP extraction with zip-slip protection
    │
    ├──→ resolveModules(moduleRoot)
    │
    ├──→ runJavadocDoclet(moduleRoot, version, modules, javadocProgress) — MODULE_JAVADOC
    │     ├──→ Start progress ticker: 5% → 100% over 100 ticks (500ms each)
    │     ├──→ Execute javadoc command with JsonDoclet
    │     ├──→ Wait up to configured timeout (default 600s)
    │     ├──→ Success: index.json exists (not exit code)
    │     └──→ Copy output → compress → cleanup
    │
    └──→ Return versionDir
```

## Data Models

### Input

```json
{
  "version": "25.0.3",
  "progressCallback": "Consumer<Progress> — may be null"
}
```

### Output

```
Path — the path to the generated documentation directory (e.g., data/25.0.3/)
```

### Progress Phases

```
MODULE_DOWNLOAD:  0% → 100% — downloading JDK distribution from Adoptium
MODULE_EXTRACT:   0% → 100% — extracting source from ZIP/TAR.GZ
MODULE_JAVADOC:   5% → 100% — running javadoc with JsonDoclet (rough estimate)
```

## Error States

- `IOException("Documenting JDK {version} is not supported yet: only modular JDKs (11+)")` — Non-modular JDK
- `IOException("The javadoc JDK (...) must be 17 or newer")` — Javadoc JDK too old (doclet needs Jackson 3)
- `IOException("The javadoc JDK (major M) cannot document newer JDK {version} source")` — Javadoc JDK older than target
- `IOException("JDK source archive not found at {srcZip}")` — Running JDK doesn't ship lib/src.zip
- `IOException("No Adoptium JDK binary found for version {version} ({os}/{arch})")` — No matching binary
- `IOException("javadoc process timed out after {timeout} seconds")` — Javadoc timed out (configured via
  `doclet.javadoc.timeout`)
- `IOException("javadoc did not produce index.json (exit code E)")` — Javadoc failure
