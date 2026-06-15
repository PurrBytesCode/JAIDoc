---
name: jdk-distribution-download-arbitrary-versions
status: pending
date: 2026-06-15
---

# Document arbitrary JDK versions via Adoptium distribution download

## Goal

Let `DocumentationService.generateJdkDocumentation(version, ...)` document a JDK version that is **not** the running
JDK, by downloading that version's distribution from Adoptium/Temurin, extracting its complete `lib/src.zip`, and
running the existing module-mode javadoc pipeline on it.

## Key technical decisions

- **Provider:** Adoptium / Temurin (`api.adoptium.net`), resolved per version + OS + architecture.
- **Scope:** modular JDKs (11, 17, 21, 25…). JDK 8 (flat, non-modular layout) is **not** handled yet — see
  "Future work: JDK 8" below. Requesting JDK 8 fails with a clear "not supported yet" error.
- **Which javadoc runs (configurable):** by default the **running JDK's** `javadoc`; overridable via
  `doclet.javadoc.home` (a JDK home — we use `<home>/bin/javadoc[.exe]`). Rationale for the default: the doclet uses
  Jackson 3, which requires JDK 17+, so an older JDK's `javadoc` (e.g. 11) cannot load the doclet; a newer javadoc
  reads older source fine. Therefore we only need the downloaded version's `lib/src.zip`, not its binaries. The
  configured/used javadoc JDK must be ≥ 17 and its major ≥ the documented source version (validated up front).
- **Constraints (validated up front):** the javadoc JDK (configured or running) must be ≥ 17 and its major ≥ the
  requested major (a newer tool reads older source, not vice-versa). Requested major must be ≥ 11.
- **Local fast-path kept:** if the requested major == the running JDK's major, use the local `lib/src.zip` (no
  download), exactly as today.
- **Caching:** downloaded archives and extracted sources are cached (idempotent); re-runs skip the download/extract.

## Pipeline (when a download is needed)

1. Resolve the Adoptium binary URL for `version` + current OS + arch.
   `GET /v3/assets/feature_releases/{major}/ga?image_type=jdk&os={os}&architecture={arch}&jvm_impl=hotspot&heap_size=normal&vendor=eclipse&page_size=50`
   → parse JSON, pick the release whose `version_data.semver` matches the requested patch, take its JDK binary
   `package.link`.
2. Download the archive (async, virtual threads, Content-Length progress, follow redirects) into a cache dir.
3. Extract `*/lib/src.zip` from the archive — `.zip` (Windows) or `.tar.gz` (Linux/macOS).
4. Extract that `src.zip` into `<work>/jdk-sources/<version>` (reuse `extractSourceZip`).
5. Run the existing `runJavadocDoclet` (module mode, running JDK's javadoc).

## Changes

1. **New `JdkDistributionDownloader`** (`com.purrbyte.ai.util`): Adoptium URL resolution + async download with
   progress. Mirrors `JdkSourceDownloader`'s patterns (virtual-thread executor, RestClient, redirect-following,
   Content-Length interceptor). Does not touch `JdkSourceDownloader`.
2. **`DocumentationService`**: inject the new downloader; branch local-vs-download in `generateJdkDocumentation`; add
   `extractSrcZipFromArchive(archive)` handling `.zip` and `.tar.gz`; add the version/runtime validations. Resolve the
   javadoc binary from the configured `doclet.javadoc.home` (default = `${java.home}`) instead of hardcoding
   `java.home` in `runJavadocDoclet`.
3. **Dependency:** `org.apache.commons:commons-compress` for `.tar.gz` extraction (cross-platform). Managed version
   if Spring Boot's BOM provides one.
4. **Config:** `jdk.distribution.download.directory` (`${JDK_DIST_DOWNLOAD_DIR:./jdk-distributions}`) and
   `doclet.javadoc.home` (`${DOCLET_JAVADOC_HOME:}`, empty = running JDK).
5. **Tests:** unit tests for OS/arch mapping, Adoptium URL building, and archive-type detection (no network); a
   network integration test (disabled by default, like `JdkSourceDownloaderIntegrationTest`) that downloads a real
   non-running version (e.g. 21.0.x) and documents `java.base`.
6. **Docs:** new `documentation/JDK-DISTRIBUTION.md` (or a section in `JDK-SOURCES.md`), update `DOCLET.md`/`TEST.md`
   /`STRUCTURE.md` as needed, and CLAUDE.md's deep-dive doc list. Update `plans/ACTIVE.md`.

## Future work: JDK 8

JDK 8's `src.zip` is a flat (non-modular) tree, so `--module-source-path` does not apply and the
`--release 8 + -subpackages` javadoc bug (see `DOCLET.md`) needs the `@argfile` workaround. Add a non-modular branch
to `runJavadocDoclet` and a JDK 8 download path when needed.

## Verification

- Unit tests (no network) green.
- With the network integration test enabled, documenting a non-running version (e.g. 21.0.x) produces `index.json`
  with the right `version` and an `api/` tree.
- The existing running-JDK fast-path (25.0.3) still works.
