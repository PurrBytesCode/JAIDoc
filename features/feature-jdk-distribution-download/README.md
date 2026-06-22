---
name: feature-jdk-distribution-download
status: implemented
date: 2026-06-21
---

# JDK Distribution Download

> Downloads full JDK distributions from Adoptium/Temurin so that arbitrary JDK versions (not just the running one) can
> be documented.

## Context

The doclet needs the complete, compilable source code of a JDK to generate Javadoc JSON. For the running JDK version,
the source comes from `lib/src.zip` bundled with the JDK. For other versions, this component queries the
Adoptium/Temurin API to find and download the matching binary archive (ZIP or TAR.GZ) for the current OS and
architecture. The archive's `lib/src.zip` is then extracted and fed to the doclet.

Downloads are cached — an already-present archive is reused. Progress is reported via a `Consumer<Progress>` callback.
The download runs asynchronously on virtual threads.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/util/JdkDistributionDownloader.java` — The downloader implementation
- `src/main/java/com/purrbyte/ai/model/dto/Progress.java` — Progress update DTO used by the download
- `src/test/java/com/purrbyte/ai/util/JdkDistributionDownloaderTest.java` — Tests for mapOs, mapArch, parseVersion,
  matchesVersion

## Scope

In: Adoptium API interaction, version matching logic, OS/architecture mapping, async download with progress reporting,
file caching
Out: JDK source extraction (covered in DocumentationService), doclet execution (covered in DocumentationService)

## Implementation

### Architecture

The downloader is a Spring `@Component` that uses `RestClient` to query the Adoptium API and download archives
asynchronously. It consists of:

1. **API interaction** — `resolveBinary()` queries the Adoptium API, paging through releases until a matching binary is
   found
2. **Version matching** — `parseVersion()` and `matchesVersion()` compare requested versions with API release versions (
   major only, major.minor, or major.minor.security)
3. **OS/Arch mapping** — `mapOs()` and `mapArch()` convert Java system properties to Adoptium API tokens
4. **Download** — `downloadDistribution()` runs the download asynchronously on virtual threads, reporting progress via
   callback

### Files

- `src/main/java/com/purrbyte/ai/util/JdkDistributionDownloader.java` — The downloader implementation

### Data Flow

```
DocumentationService.generateJdkDocumentation(version)
    │
    ├──→ JdkDistributionDownloader.downloadDistribution(version, progress)
    │     │
    │     ├──→ resolveBinary(version, os, arch)
    │     │     └── Query Adoptium API: GET /v3/assets/feature_releases/{major}/ga
    │     │           └── Match version, OS, architecture → AdoptiumPackage
    │     │
    │     ├──→ Check cache: Files.exists(targetFile) → reuse or download
    │     │
    │     └──→ Download with progress:
    │           └── REST client GET archive link
    │               └── Write to .part file with 64KB buffer
    │                   └── progressCallback.accept(Progress.of(progress, MODULE_DOWNLOAD))
    │                       └── Move .part → target file
    │
    └──→ DocumentationService.extractSourceZip(downloadedPath)
```

### Configuration

- `src/main/resources/configurations/db-configuration.yml` — `jdk.distribution.download.directory` — Download cache
  directory (default: `${DATA_DIR:./data}/jdk-distributions`)

## Tests

- `src/test/java/com/purrbyte/ai/util/JdkDistributionDownloaderTest.java` — Unit tests for pure logic (no HTTP):
    - `MapOsTest`: windows, mac, linux
    - `MapArchTest`: amd64→x64, x86_64→x64, aarch64, arm64→aarch64
    - `ParseVersionTest`: full version (21.0.11), major only (21), stripped JDK prefix (jdk-17.0.13), stripped build
      suffix (21.0.11+10)
    - `MatchesVersionTest`: exact match, major-only matches any patch, security mismatch, major mismatch

## Notes

- The Adoptium API endpoint is `https://api.adoptium.net/v3/assets/feature_releases/{major}/ga` — it returns GA (General
  Availability) releases for the given major version
- The API is paged with `MAX_PAGES=6` and `PAGE_SIZE=50`, meaning up to 300 releases are checked
- The download uses a `.part` file that is atomically renamed to the target file on completion — this ensures partial
  downloads are never mistaken for complete ones
- The `RestClient` is configured with `setInstanceFollowRedirects(true)` to follow HTTP redirects (Adoptium URLs often
  redirect)
- The virtual thread executor (`Executors.newVirtualThreadPerTaskExecutor()`) is used instead of a fixed thread pool —
  this allows many concurrent downloads without thread exhaustion
