# Reverse-Engineer JAIDoc Feature Workspaces

## Overview

This plan documents the step-by-step process for creating feature workspaces that fully explain the JAIDoc software as it exists today. The goal is to create comprehensive, self-contained context bundles that an AI (or a developer) could follow to recreate the system from scratch.

---

## Feature Ordering (Dependency-Based)

Features must be created in dependency order - foundational layers first, then features that depend on them. Here is the ordered list:

### Phase 1: Foundational Layers (No Dependencies)

1. **Data Models** - JdkVersion, JdkDocElement, JdkDocChunk entities (0 dependencies)
2. **JPA Embedding Converter** - FloatArrayConverter (depends on domain models for context)
3. **ZIP Helper** - ZIPHelper (standalone utility)
4. **Model Classes** - ElementKind, IngestStatus, Progress, JdkSearchResult DTOs

### Phase 2: Core Processing Pipelines (Depend on Phase 1)

5. **JDK Distribution Download** - JdkDistributionDownloader (standalone but used by DocService)
6. **JSON Doclet** - JsonDoclet, TypeJsonBuilder, DocTreeJson, ChunkWriter (standalone but used by DocService)

### Phase 3: Service Layer (Depend on Phase 2)

7. **Documentation Service** - DocumentationService (depends on JdkDistributionDownloader + JsonDoclet)
8. **Embedding Service** - EmbeddingService (depends on Spring AI, standalone otherwise)

### Phase 4: Higher-Level Services (Depend on Phase 3)

9. **Database Ingestion** - IngestionService (depends on DocumentationService + EmbeddingService)
10. **Auto-Discovery Ingestion** - IngestDiscoveryService (depends on IngestionService)
11. **Semantic Search** - JdkSearchService (depends on EmbeddingService + domain models)

### Phase 5: API Layer (Depend on Phase 4)

12. **MCP Tools** - JavaDocMCP, SpringBootMCP (depends on JdkSearchService)
13. **MCP Configuration** - McpToolsConfiguration (depends on MCP tool classes)

### Cross-Cutting Concerns

14. **Application Bootstrap** - JAIDoc main class, ObjectMapperConfiguration
15. **Repository Layer** - JdkVersionRepository, JdkDocElementRepository, JdkDocChunkRepository (depends on domain models)
16. **Configuration** - All YAML configuration files (cross-cutting, documented separately)

---

## File Structure for Each Feature Workspace

Each feature workspace will contain:

### Required Files

- `README.md` - Entry point with YAML frontmatter + sections (Context, Feature Inputs, Scope, Implementation, Tests, Notes)
- `data-flow.md` - End-to-end data flow with sequence diagram, data models, error states
- `mock-requests.json` - Example request/response pairs

### Optional Files

- `notes.md` - Edge cases, gotchas, observations
- `scripts/validate.sh` - Validation script (same as the example)

---

## Content Depth Guidelines

### README.md (Required)

- **YAML Frontmatter**: name, status, date
- **Context**: 1-2 paragraphs explaining what this feature does and why it exists
- **Feature Inputs**: List of source files that are part of this feature, with brief descriptions
- **Scope**: What's in and what's out
- **Implementation**:
  - **Architecture**: How data flows through the feature, key components
  - **Files**: Bullet list of source files with one-line descriptions
  - **Data Flow**: ASCII diagram showing the flow
  - **Configuration**: Which configuration keys affect this feature
- **Tests**: List of test files with descriptions
- **Notes**: Edge cases and observations

### data-flow.md (Required)

- **Overview**: 2-3 sentences describing the end-to-end flow
- **Sequence Diagram**: ASCII art showing the component interactions
- **Data Models**: Input JSON schema, output JSON schema
- **Error States**: What can go wrong and how it's handled

### mock-requests.json (Required)

- Aligned with the actual API contract
- Use real class names, package names, and method signatures from the JDK

---

## Step-by-Step Creation Plan

### Step 1: Data Models (Estimated effort: 2-3 hours)

**Feature Name**: feature-data-models

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/domain/JdkVersion.java - Tracks JDK versions with metadata
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/domain/JdkDocElement.java - Structural Javadoc elements (modules, packages, types)
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/domain/JdkDocChunk.java - Text chunks for semantic search, indexed with Hibernate Search

**Test Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/model/converter/FloatArrayConverterTest.java - Tests the converter used by JdkDocChunk

**Content to Include**:
- Full entity definitions with all fields and annotations
- Explanation of the entity relationships (ManyToOne, OneToMany)
- The Hibernate Search annotations on JdkDocChunk (@Indexed, @VectorField, @KeywordField, @FullTextField)
- The UUID primary key strategy
- The unique constraint on (jdk_version_id, chunk_id)
- The denormalized version field used for kNN filtering

**Mock Data**: Show example of a JdkVersion entity state, a JdkDocElement, and a JdkDocChunk with embedding.

### Step 2: JPA Embedding Converter (Estimated effort: 1 hour)

**Feature Name**: feature-embedding-converter

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/model/converter/FloatArrayConverter.java - Converts float[] to/from byte[] BLOB for SQLite storage

**Test Files**:
- Same as above (already covered in Step 1)

**Content to Include**:
- The 384-dimension embedding array format
- The ByteBuffer/FloatBuffer serialization logic
- Why this is needed (SQLite has no native vector column type)
- The null handling on both conversion directions

### Step 3: ZIP Helper (Estimated effort: 1 hour)

**Feature Name**: feature-zip-helper

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/util/ZIPHelper.java - Finds entries in ZIP files with version-prefixed path handling

**Test Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/util/ZIPHelperTest.java - 8 test cases covering exact match, version-prefixed, windows-style paths, deep directories, non-existent entries, empty ZIPs

**Content to Include**:
- The version-prefixed directory search logic (entries may be under jdk-25.0.3/ or 21/ or deeper)
- Windows-style path handling (backslash vs forward slash)
- Edge case: multiple version directories - returns first match
- Edge case: deep version directories (e.g., jdk-25.0.3-ga/modules/java.base/index.json)

### Step 4: Model Classes (Estimated effort: 1 hour)

**Feature Name**: feature-model-classes

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/model/ElementKind.java - MODULE, PACKAGE, TYPE enum
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/model/IngestStatus.java - INGESTING, READY, FAILED enum
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/model/dto/Progress.java - Progress update with percentage and module phase
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/model/dto/JdkSearchResult.java - Search result DTO with chunkId, kind, qualifiedType, member, signature, text, score, rawJson

**Content to Include**:
- The enum values and their meanings
- Progress phases: MODULE_DOWNLOAD, MODULE_EXTRACT, MODULE_JAVADOC
- The BigDecimal rounding to 2 decimal places in Progress
- The JdkSearchResult fields and how they map to the search result

### Step 5: JDK Distribution Download (Estimated effort: 2-3 hours)

**Feature Name**: feature-jdk-distribution-download

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/util/JdkDistributionDownloader.java - Downloads JDK from Adoptium/Temurin API

**Test Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/util/JdkDistributionDownloaderTest.java

**Content to Include**:
- The Adoptium API endpoint structure: https://api.adoptium.net/v3/assets/feature_releases/{major}/ga
- The query parameters: architecture, heap_size, image_type, jvm_impl, os, vendor, page, page_size, sort_order
- The version matching logic: parseVersion extracts [major, minor, security], matchesVersion compares them (minor/security only compared when requested)
- OS mapping: win->windows, mac/darwin->mac, else linux
- Architecture mapping: amd64/x86_64/x64->x64, aarch64/arm64->aarch64
- The paging logic: MAX_PAGES=6, PAGE_SIZE=50
- The virtual thread executor (Executors.newVirtualThreadPerTaskExecutor())
- The progress reporting via Consumer<Progress>
- The download with progress tracking (16KB buffer)
- The file caching (already downloaded archives are reused)
- The record types: AdoptiumRelease, VersionData, AdoptiumBinary, AdoptiumPackage

**Configuration**:
- jdk.distribution.download.directory - Download cache directory

**Mock Data**: Show the Adoptium API response structure and the resolved binary.

### Step 6: JSON Doclet (Estimated effort: 3-4 hours)

**Feature Name**: feature-json-doclet

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/doclet/JsonDoclet.java - Main doclet, CLI options, orchestration
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/doclet/TypeJsonBuilder.java - Converts language model elements to JSON and emits chunks
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/doclet/DocTreeJson.java - Serializes Javadoc comment trees to structured JSON + plain text
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/doclet/ChunkWriter.java - Writes chunks.jsonl with smart text splitting

**Test Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/doclet/ChunkWriterTest.java - 10 test cases for text splitting
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/doclet/DocTreeJsonTest.java

**Content to Include**:
- The CLI options: -d, --doc-version, --pretty, --no-chunks, --chunks-file, --max-chunk-chars, --chunk-overlap, --only-documented
- The output structure: api/<package>/<Type>.json, module-<name>.json, index.json, chunks.jsonl
- The ChunkWriter splitting logic: maxChars (default 4000), overlap (default 200), prefers paragraph > line > sentence > hard cut
- The DocTreeJson serialization of all DocTree node types (forward compatibility for JDK 18+ nodes)
- The fullText method: body + block tags combined for embeddings
- The chunkId generation: module:name, package:name, qualifiedName for types, qualifiedName#method(params) for methods
- The metadata structure on chunks: kind, documented, type, package, module, member, signature, since, deprecated, file, line

**Configuration**:
- doclet.work.directory - Working directory for doclet output
- doclet.jar.directory - Directory containing JAIDoc-doclet.jar
- doclet.javadoc.home - JDK home for running javadoc
- doclet.modules - Comma-separated module list

### Step 7: Documentation Service (Estimated effort: 2-3 hours)

**Feature Name**: feature-documentation-service

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/service/DocumentationService.java - Orchestrates the entire doclet pipeline

**Test Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/service/DocumentationServiceTest.java - Unit tests for resolveDocletJarPath, listAvailableVersions, isVersionGenerated, getVersionZip, extractSourceZip

**Content to Include**:
- The 3-phase pipeline: download/extract -> run javadoc -> compress
- The source selection logic: local src.zip if version matches running JDK, otherwise download from Adoptium
- The lib/src.zip extraction from both .zip and .tar.gz archives
- The zip-slip protection in extraction
- The javadoc command construction: -docletpath, -doclet, --module-source-path, --module, -d, --pretty, --doc-version, -Xmaxerrs, -Xmaxwarns
- The progress ticker during javadoc (5% to 100% over 100 ticks, every 500ms)
- The timeout: 600 seconds for javadoc
- The success detection: presence of index.json (not exit code)
- The ZIP compression and cleanup of the version directory
- The version listing from database (not filesystem)

**Configuration**:
- jdk.distribution.download.directory - Download cache
- data.directory - Output directory for documentation
- doclet.work.directory - Working directory
- doclet.jar.directory - Doclet JAR directory
- doclet.javadoc.home - JDK home for javadoc
- doclet.modules - Modules to document

### Step 8: Embedding Service (Estimated effort: 1 hour)

**Feature Name**: feature-embedding-service

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/service/EmbeddingService.java - Thin wrapper over Spring AI EmbeddingModel

**Content to Include**:
- The "passage: " and "query: " prefixes required by the multilingual-e5 model family
- The two methods: embedPassage (for indexing) and embedQuery (for search)
- The 384-dimensional embedding output
- The ONNX model configuration: model URI, tokenizer URI

**Configuration**:
- spring.ai.embedding.transformer.onnx.model-uri - ONNX model path
- spring.ai.embedding.transformer.tokenizer.uri - Tokenizer JSON path

### Step 9: Database Ingestion (Estimated effort: 2-3 hours)

**Feature Name**: feature-database-ingestion

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/service/IngestionService.java - Reads ZIP, ingests elements and chunks into the database

**Test Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/test/java/com/purrbyte/ai/service/IngestionSearchIntegrationTest.java - Integration test for ingest + search

**Content to Include**:
- The 3-phase ingestion: read manifest -> ingest elements -> ingest chunks
- The manifest parsing: version, javaRuntime, generator, generatedAt, typeCount, chunkCount, packageCount, moduleCount
- The structural element ingestion: reads all .json files (except index.json), creates JdkDocElement records
- The batch size: 200 records per flush
- The chunk ingestion: reads chunks.jsonl, generates embeddings, links to JdkDocElement via qualifiedId
- The status management: INGESTING -> READY on success, FAILED on error
- The idempotent behavior: existing versions are returned as-is
- The duration logging throughout

### Step 10: Auto-Discovery Ingestion (Estimated effort: 1 hour)

**Feature Name**: feature-auto-discovery-ingestion

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/service/IngestDiscoveryService.java - Auto-discovers and ingests ZIP files on startup

**Content to Include**:
- The ApplicationReadyEvent listener
- The file walk: Files.walk(dataDirectory), filter .zip files
- The version extraction from filename (strip .zip)
- The per-version processing: check DB status, trigger ingestion if not READY
- Error isolation: one version's failure does not block others
- The discovery count logging

**Configuration**:
- ingest.enabled - Enable/disable ingestion (default: true)
- ingest.scan.auto - Enable/disable auto-scan (default: true)
- data.directory - Directory to scan

### Step 11: Semantic Search (Estimated effort: 1 hour)

**Feature Name**: feature-semantic-search

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/service/JdkSearchService.java - Embeds query, performs kNN search on JdkDocChunk

**Content to Include**:
- The embedding of the query with "query: " prefix
- The Hibernate Search kNN query structure
- The version filter: searches one version at a time via f.match().field("version").matching(version)
- The result composite: f.composite().from(f.entity(), f.score()).as(this::toResult)
- The JdkSearchResult construction from chunk + score + rawJson

**Configuration**:
- spring.ai.embedding.transformer.onnx.model-uri - Same embedding model used for indexing

### Step 12: MCP Tools (Estimated effort: 1 hour)

**Feature Name**: feature-mcp-tools

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/mcp/JavaDocMCP.java - MCP tool annotations for listVersions and searchJavadoc
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/mcp/SpringBootMCP.java - Placeholder Spring Boot MCP tool

**Content to Include**:
- The @Tool annotation pattern: description, method parameters with @ToolParam
- The listVersions() tool: returns DocumentationService.listAvailableVersions()
- The searchJavadoc(version, query, topK) tool: calls JdkSearchService.search(), defaults topK to 10
- The SpringBootMCP placeholder: TODO implementation, returns placeholder text

### Step 13: MCP Configuration (Estimated effort: 1 hour)

**Feature Name**: feature-mcp-configuration

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/configuration/McpToolsConfiguration.java - Registers MCP tool callbacks as Spring beans

**Content to Include**:
- The MethodToolCallbackProvider pattern
- The two beans: javadocToolCallbacks and springBootToolCallbacks
- How the @Tool annotations are discovered and registered

### Step 14: Application Bootstrap (Estimated effort: 1 hour)

**Feature Name**: feature-application-bootstrap

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/JAIDoc.java - Main class, startup configuration
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/configuration/ObjectMapperConfiguration.java - Jackson 3 JSON mapper customizer

**Content to Include**:
- The APP_ID generation (UUID)
- The BufferingApplicationStartup setup: 2048 buffer, filter for "spring.beans.instantiate"
- The JsonMapperBuilderCustomizer: disable DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, enable FAIL_ON_UNKNOWN_PROPERTIES
- The XML and YAML mapper configurations

### Step 15: Repository Layer (Estimated effort: 1 hour)

**Feature Name**: feature-repository-layer

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/repository/JdkVersionRepository.java - Version queries, version ordering
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/repository/JdkDocElementRepository.java - Element lookup by qualifiedId
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/com/purrbyte/ai/repository/JdkDocChunkRepository.java - Base JPA repository

**Content to Include**:
- The custom query: SELECT v.version FROM JdkVersion v WHERE v.status = 'READY' ORDER BY v.major DESC, v.minor DESC, v.security DESC
- The findByJdkVersionAndQualifiedId composite key lookup
- The base JPA repositories for chunks (simple CRUD)

### Step 16: Configuration (Estimated effort: 2 hours)

**Feature Name**: feature-configuration

**Source Files**:
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/application.yaml - Main config, profile imports
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/db-configuration.yml - SQLite datasource, Hibernate Search index directory
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/ai-configuration.yml - ONNX model and tokenizer URIs
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/mcp-configuration.yml - MCP server settings (streamable protocol)
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/search-configuration.yml - Hibernate Search index config
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/ingest-configuration.yml - Ingestion enable/disable settings
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/logging-configuration.yml - Logback rolling policy
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/springdoc-configuration.yml - Swagger UI settings
- /c/Users/aluis/IdeaProjects/JAIDoc/src/main/resources/configurations/actuator-configuration.yml - Actuator endpoint settings

**Content to Include**:
- The full configuration hierarchy (11 config files imported via spring.config.import)
- All configuration keys with their default values
- Environment variable overrides (e.g., ${DATA_DIR:./data})
- The virtual threads enablement
- The Hibernate Search Lucene directory configuration (local-filesystem)
- The SQLite dialect configuration

---

## Consistency Checks

### Cross-Feature Consistency

1. **Naming Consistency**: All feature workspace directories must use kebab-case with feature- prefix
2. **Status Values**: All README.md frontmatter must use one of: template, not implemented, implemented
3. **File References**: All source file paths in feature READMEs must use absolute paths (e.g., /c/Users/aluis/IdeaProjects/JAIDoc/src/main/java/...)
4. **Configuration Keys**: Configuration keys referenced in feature READMEs must match the actual YAML keys in the configuration files
5. **API Contract**: Mock request/response pairs in mock-requests.json must match the actual API contract (method signatures, parameter names, return types)

### Internal Consistency

1. **Data Models**: Ensure that the JdkVersion/JdkDocElement/JdkDocChunk relationships described in the Data Models feature match the entity definitions used in other features
2. **Embedding Dimensions**: The 384-dimension embedding must be consistently referenced across Embedding Service, JPA Embedding Converter, and Semantic Search features
3. **Version Format**: The version format (e.g., "25.0.3") must be consistent across all features - parsed by parseVersion into [major, minor, security]
4. **Progress Phases**: The three progress phases (MODULE_DOWNLOAD, MODULE_EXTRACT, MODULE_JAVADOC) must be consistently described in Documentation Service and JDK Distribution Download features

### Validation

- Run scripts/validate.sh on each feature workspace to ensure required files exist and frontmatter is correct
- Cross-reference all file paths against the actual repository structure
- Verify that all test file references exist in the repository

---

## Update Strategy

### When Code Changes

1. Identify affected features: Map the changed source files to their feature workspaces
2. Update the affected feature README.md: Refresh file descriptions, architecture diagrams, and data flow
3. Update related features: If a change affects multiple features (e.g., changing JdkDocChunk fields), update all dependent features
4. Update mock-requests.json: If API contracts change, update the mock request/response pairs
5. Re-run validation: Run validate.sh on all affected feature workspaces

### When Architecture Changes

1. Re-examine data flow: If the pipeline changes (e.g., adding a new phase), update data-flow.md in all affected features
2. Update sequence diagrams: ASCII art diagrams must reflect the current architecture
3. Consider feature reorganization: If a new sub-feature emerges, split or merge feature workspaces accordingly

### When New Features Are Added

1. Determine dependency order: Place the new feature in the correct phase based on its dependencies
2. Create the feature workspace: Follow the established file structure and content guidelines
3. Update dependent features: If the new feature changes how existing features work, update their documentation
4. Update the FEATURES.md index: Add the new feature to the table of contents

### When Documentation Changes

1. Cross-reference documentation: The deep-dive docs in documentation/ should be referenced in feature READMEs
2. Update feature notes: Notes.md should capture any insights from the deep-dive documentation

---

## Estimated Total Effort

- Phase 1 (Foundational): ~5 hours
- Phase 2 (Core Processing): ~5-7 hours
- Phase 3 (Service Layer): ~3-4 hours
- Phase 4 (Higher-Level Services): ~4-5 hours
- Phase 5 (API Layer): ~2-3 hours
- Cross-Cutting: ~4-5 hours

**Total: ~23-29 hours** (spread across 16 feature workspaces + configuration)

---

## External Dependencies

### APIs
- **Adoptium/Temurin API**: https://api.adoptium.net/v3/assets/feature_releases/{major}/ga - JDK distribution metadata
  - Query parameters: architecture, heap_size, image_type, jvm_impl, os, vendor, page, page_size, sort_order

### AI Models
- **ONNX Embedding Model**: model_qint8_avx512_vnni.onnx (118 MB) - 384-dimensional vector embeddings
- **Tokenizer**: tokenizer.json (17 MB) - JSON tokenizer for the embedding model
- The model family is "multilingual-e5" which requires "passage: " / "query: " prefixes

### Databases
- **SQLite**: jaidoc.sqlite - The primary database, accessed via JDBC
- **Hibernate Search/Lucene**: Local filesystem directory (./jaidoc-index) for full-text search and kNN indexing
- The index uses HNSW vector index (Lucene99HnswVectorsFormat)

### Build Tools
- **Maven**: For building the project and assembling the doclet JAR
- **Spring Boot 4.1.0**: Application framework
- **Spring AI 2.0.0**: MCP server and embedding model integration
- **Hibernate Search 8.4.0**: Vector search via kNN
- **Jackson 3** (tools.jackson.*): JSON serialization, requires Java 17+
- **Spring Cloud 2025.1.2**: Dependency management
- **Lombok**: Code generation
- **Java 25**: Target JDK version

### Runtime Requirements
- **Virtual threads**: Enabled via spring.threads.virtual.enabled=true
- **SQLite JDBC driver**: For database access
- **Hibernate Community Dialects**: SQLiteDialect for Hibernate
- **Commons Compress**: For tar.gz extraction
- **Spring Boot Actuator**: Health and metrics endpoints
- **SpringDoc OpenAPI**: Swagger UI for API documentation
- **Logback**: Logging framework

---

## Key Patterns to Document

1. **Idempotent Operations**: ZIP extraction, ingestion, and version checking all check for existing state before acting
2. **Error Isolation**: IngestDiscoveryService processes each version independently; one failure does not block others
3. **Progress Reporting**: Consumer<T> callback pattern with phase-specific progress (0-100%)
4. **Version Resolution**: Adoptium API paging with version matching (major only, major.minor, or major.minor.security)
5. **Chunk Splitting Strategy**: Paragraph > line > sentence > hard cut, with configurable overlap
6. **Embedding Prefix Convention**: "passage: " / "query: " prefixes for semantic compatibility
7. **Denormalized Search Fields**: JdkDocChunk.version field enables kNN filtering without JOINs
8. **Filesystem-Based Indexing**: Hibernate Search Lucene directory on local filesystem, not embedded
9. **ZIP-Based Archive Format**: Documentation is compressed into version-prefixed ZIPs for storage efficiency
10. **Config-Driven Architecture**: Nearly all paths and limits are configurable via application.yaml or environment variables
