---
name: fix-javadoc-generation-srczip
status: completed
date: 2026-06-15
---

# Fix JDK Javadoc generation (DocumentationServiceIntegrationTest)

## Problem (root causes, verified end-to-end)

1. **Doclet JAR is incomplete.** `assembly/doclet-jar.xml` bundles only `jackson-core` and
   `jackson-databind`, but Jackson 3 (`tools.jackson.*`) also needs `jackson-annotations`. The doclet
   crashes with `NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonSerializeAs` the moment it
   builds a `JsonMapper`.

2. **Wrong source + incomplete source.** `DocumentationService.runJavadocDoclet` ran
   `-sourcepath <root>/src -subpackages java jdk com.sun ...`. The OpenJDK GitHub tree is **modular**
   (`src/<module>/share/classes/<pkg>`), so `-subpackages` finds nothing → `error: No source files for
   package jdk` → exit code 2. Worse, the GitHub tree is **missing build-time-generated sources**
   (`ByteBuffer`, `CharBuffer`, charsets…), so type attribution fails and javadoc never runs the doclet
   (hundreds of `cannot find symbol`). The fix: use the JDK's own complete `lib/src.zip`.

3. **Test asserts don't match output.** The doclet writes `index.json` (with a `packages` array) — not a
   `packages.json` — and writes `javaRuntime`, not `version`.

## Verified working approach (manual)

`javadoc -docletpath <doclet.jar>;<jackson-annotations.jar> -doclet com.purrbyte.ai.doclet.JsonDoclet
--module-source-path <srcZipExtractRoot> --module java.base -d <out> --pretty -Xmaxerrs 100000
-Xmaxwarns 100000` → exit 0, 1391 types, 34995 chunks, `module-java.base.json`, in ~12s.

## Decisions

- **Source:** the running JDK's `lib/src.zip` (`java.home/lib/src.zip`). Documents the JDK matching
  `JAVA_HOME`. `JdkSourceDownloader` stays in the codebase (still has its own tests) but is no longer
  wired into `DocumentationService`.
- **Module scope:** configurable via `doclet.modules` (`application.yaml`); empty default = all modules
  found in the extracted src.zip. Integration test limits to `java.base`.

## Changes

1. `assembly/doclet-jar.xml`: add `com.fasterxml.jackson.core:jackson-annotations` to the dependency
   includes.
2. `JsonDoclet`: add a `--doc-version <v>` option; write `"version"` into `index.json`.
3. `DocumentationService`:
   - Drop the `JdkSourceDownloader` constructor dependency; add `@Value("${doclet.modules:}") String`.
   - New pipeline: locate `lib/src.zip` → validate major version matches running JDK → extract (zip-slip
     safe, idempotent) into `workDir/jdk-sources/<version>` → enumerate modules (or use configured list)
     → run javadoc in module mode with `-Xmaxerrs/-Xmaxwarns` → treat as success if `index.json` is
     produced (lenient exit code) → copy to `outputDir/<version>`.
   - `extractSourceZip` returns the extract dir (module-source-path root); remove `findSourceRoot`.
4. Config: `documentation-configuration.yml` adds `doclet.modules` (default empty = all).
5. Tests:
   - `DocumentationServiceIntegrationTest`: construct service with `(workDir, outputDir, "java.base")`;
     assert `index.json` exists, `api/` dir exists, index content has `"version"` and `"packages"`.
   - `DocumentationServiceTest`: update constructor calls; update `extractSourceZip` expectations to the
     extract dir; remove `findSourceRoot` tests.
6. Docs: update `documentation/DOCLET.md` (`--doc-version`, `version` field, src.zip-based generation)
   and `documentation/STRUCTURE.md` / `documentation/TEST.md` if affected.

## Verification

- Rebuild the doclet JAR (`mvn -q -DskipTests package` or assembly:single) so jackson-annotations and
  `--doc-version` are present.
- Run `DocumentationServiceIntegrationTest` with `-Dtest.integration.enabled=true` → green.
- Run `DocumentationServiceTest` (unit) → green.

## Follow-up: configurable doclet directory

`DocumentationServiceTest.ResolveDocletJarPathTest` used to delete / overwrite the committed
`doclet/JAIDoc-doclet.jar` in the real project directory, so every `mvn test` wiped the runtime artifact
and dirtied git. Fix:

- `DocumentationService` now takes `@Value("${doclet.jar.directory:doclet}") Path docletDirectory` and
  `resolveDocletPath()` reads from it instead of `<user.dir>/doclet`.
- `documentation-configuration.yml` adds `doclet.jar.directory` (`${DOCLET_JAR_DIR:doclet}`).
- The unit tests point `docletDirectory` at a `@TempDir`; the integration test passes the real
  `<user.dir>/doclet`. No test touches the committed JAR anymore.
