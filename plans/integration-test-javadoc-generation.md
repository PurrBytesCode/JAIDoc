---
name: integration-test-javadoc-generation
status: completed
date: 2026-06-14
---

# Integration Test for JavaDoc Generation

## Context

Need an integration test to verify the `DocumentationService` works end-to-end — downloading JDK 25.0.3 source,
extracting it, running the JsonDoclet javadoc processor, and producing JSON documentation files.

## Changes

### 1. `src/test/resources/application-test.yaml` — Add test-specific directories for JavaDoc generation

```yaml
doclet:
  work:
    directory: target/test-jdk-doc-workspace
  output:
    directory: target/test-javadoc-output
```

### 2. `src/test/java/com/purrbyte/ai/service/DocumentationServiceIntegrationTest.java` — New integration test

- Extends `IntegrationTest` (no Spring context needed — constructs service manually)
- Uses `@TempDir` for the JdkSourceDownloader download directory
- Test method `generateJdkDocumentation_jdk25_0_3_producesJsonOutput()`:
    1. Creates a JdkSourceDownloader with temp directory
    2. Calls `documentationService.generateJdkDocumentation("25.0.3", p -> log.info("Progress: {}%", p))`
    3. Waits for the `CompletableFuture<Path>` to complete
    4. Verifies `index.json`, `packages.json`, and `index.json` content

## Verification

```
mvn test -Dtest=DocumentationServiceIntegrationTest -Dtest.integration.enabled=true
```
