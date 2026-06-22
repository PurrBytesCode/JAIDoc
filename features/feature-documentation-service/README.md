---
name: feature-documentation-service
status: implemented
date: 2026-06-21
---

# Documentation Service

> Orchestrates the entire JDK documentation generation pipeline: source acquisition, extraction, javadoc execution, and output compression.

## Context

The DocumentationService is the central orchestrator of the JAIDoc pipeline. It connects the JDK distribution downloader, the JSON doclet, and the database ingestion service into a cohesive workflow. For each JDK version, it:

1. Determines the source: local `lib/src.zip` if the version matches the running JDK, or downloads from Adoptium
2. Extracts the source archive with zip-slip protection
3. Runs the javadoc command with the JsonDoclet in module mode
4. Compresses the output into a version-prefixed ZIP and deletes the extracted directory

The pipeline is fully asynchronous (virtual threads) and reports progress through three phases: MODULE_DOWNLOAD, MODULE_EXTRACT, MODULE_JAVADOC.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/service/DocumentationService.java` — The service implementation
- `src/main/java/com/purrbyte/ai/util/JdkDistributionDownloader.java` — JDK distribution download
- `src/main/java/com/purrbyte/ai/util/ZIPHelper.java` — ZIP entry lookup
- `src/test/java/com/purrbyte/ai/service/DocumentationServiceTest.java` — Unit tests
- `src/test/java/com/purrbyte/ai/service/DocumentationServiceIntegrationTest.java` — E2E integration tests

## Scope

In: Pipeline orchestration, source selection logic, ZIP extraction with zip-slip protection, javadoc command construction and execution, progress reporting, output compression and cleanup, version listing
Out: The doclet itself (covered in feature-json-doclet), embedding generation (covered in EmbeddingService), database ingestion (covered in Database Ingestion)

## Implementation

### Architecture

The service is a Spring `@Service` that uses virtual threads for async pipeline execution. It consists of several tightly coupled methods:

1. **`generateJdkDocumentation(version, progress)`** — The main entry point. Orchestrates the full pipeline: source acquisition → extraction → javadoc execution → compression
2. **`extractSourceZip(zipFile, version, progress)`** — Extracts a source ZIP with zip-slip protection, reports progress based on file count
3. **`runJavadocDoclet(moduleRoot, version, modules, progress)`** — Executes the javadoc command with the JsonDoclet, handles timeout, success detection (presence of index.json, not exit code), and output compression
4. **`zipVersion(versionDir, version)`** — Compresses the version directory into a ZIP and deletes the original
5. **`listAvailableVersions()`** — Lists versions from the database (not filesystem)
6. **`isVersionGenerated(version)`** — Checks if a version's documentation ZIP exists and contains index.json

### Files

- `src/main/java/com/purrbyte/ai/service/DocumentationService.java` — The service implementation

### Data Flow

```
DocumentationService.generateJdkDocumentation(version, progress)
    │
    ├──→ validateRequest(version, major) — checks JDK 11+ modular, javadoc 17+
    │
    ├──→ Source acquisition:
    │     ├──→ localSrcZip() if version == running JDK
    │     └──→ downloadDistribution(version, progress) → extractSrcZipFromArchive()
    │
    ├──→ extractSourceZip(sourceZip, version, extractProgress)
    │     └── ZIP extraction with zip-slip protection
    │
    ├──→ resolveModules(moduleRoot) — configured modules or all modules with module-info.java
    │
    ├──→ runJavadocDoclet(moduleRoot, version, modules, javadocProgress)
    │     ├──→ Start progress ticker (5% to 100% over 100 ticks, every 500ms)
    │     ├──→ Execute: javadoc -docletpath <jar> -doclet JsonDoclet
    │     │          --module-source-path <root> --module <modules> -d <out> --pretty --doc-version <ver>
    │     │          -Xmaxerrs 100000 -Xmaxwarns 100000
    │     ├──→ Wait up to 600 seconds
    │     ├──→ Success: presence of index.json (not exit code)
    │     └──→ Copy output to data directory, compress, cleanup
    │
    └──→ Return versionDir
```

### Configuration

- `src/main/resources/configurations/db-configuration.yml` — `doclet.work.directory`, `doclet.jar.directory`, `doclet.javadoc.home`, `doclet.modules`, `data.directory`

## Tests

- `src/test/java/com/purrbyte/ai/service/DocumentationServiceTest.java` — Unit tests:
  - `ResolveDocletJarPathTest`: no jar returns null, jar present returns path
  - `ListAvailableVersionsTest`: empty list, one version, multiple versions ordered by major descending
  - `IsVersionGeneratedTest`: version with index.json in ZIP (true), version without index.json (false), non-existent version (false)
  - `GetVersionZipTest`: existing version at root, existing version in subdir, non-existent version
  - `ExtractSourceZipTest`: extract creates dir with contents, idempotent reuse, zip-slip attack prevention

- `src/test/java/com/purrbyte/ai/service/DocumentationServiceIntegrationTest.java` — E2E tests:
  - `generateJdkDocumentation_jdk25_0_3_producesJsonOutput` — Documents the running JDK from local src.zip
  - `generateJdkDocumentation_downloadsNonRunningVersion_producesJsonOutput` — Downloads a non-running JDK (21.0.11) from Adoptium

## Notes

- Success is determined by the presence of `index.json` in the output directory, not by the javadoc exit code — the doclet runs after type attribution, so missing generated symbols can leave a non-zero exit code even though valid output was produced
- The progress ticker during javadoc runs from 5% to 100% over 100 ticks (every 500ms) — this is a rough estimate since javadoc doesn't provide actual progress
- The `zipVersion()` method compresses the version directory into a ZIP and then deletes the extracted directory — this saves disk space for cached documentation
- The `--Xmaxerrs 100000 --Xmaxwarns 100000` flags are set because the JDK source references build-time-generated symbols that may be absent, and the default diagnostic limits would cause javadoc to abort
