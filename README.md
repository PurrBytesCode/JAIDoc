# JAIDoc

[![Java](https://img.shields.io/badge/Java-25-red.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9.15-blue.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

JAIDoc is an **exercise in creating a Model Context Protocol (MCP) server** that makes JDK and Spring Boot documentation
searchable and consumable by AI models. It's a practical example of how to bridge the gap between traditional technical
documentation and AI-driven development workflows — entirely with local AI.

## Why JAIDoc?

The official Java and Spring Boot documentation is vast, well-maintained, and constantly updated — but it's locked
behind HTML pages, versioned separately, and not queryable by AI models in context. When you're coding and need to
verify how a method works or what a class does, you have to leave your IDE, search Google, navigate to the docs site,
and find the right version.

JAIDoc is also an **example for the community** on how to organize, track, and expose technical documentation through
MCP tools. The JDK SDK integration is complete — database layer, embedding model, semantic search, and MCP tools are all
functional. The project aims to grow into Spring Boot — where documentation is far more complex (migration guides,
how-to guides, AsciiDoc formats, cross-references) — and serve as a reference for building your own documentation MCP
servers.

JAIDoc solves this by letting the local AI model (Qwen 3.6, Gemma-4, or QWOPUS — running on your machine) answer these
questions directly, without relying on cloud APIs or sending code context to third-party services.

It does this by:

1. **Converting** official documentation into structured JSON via a Java Doclet
2. **Indexing** it for semantic search (vector embeddings, Hibernate Search/Lucene)
3. **Exposing** it through the MCP protocol so AI models can query it directly

- **Doclet pipeline**: JDK source → `JsonDoclet` → JSON Javadoc (fully implemented)
- **MCP tools**: `listVersions()`, `searchJavadoc()`, `startDocGeneration()`, `getDocGenerationProgress()`,
  `startIngest()`, `getIngestProgress()` — fully wired to the semantic search service

This project demonstrates the full stack: doclet → JSON → SQLite + Hibernate Search/Lucene → MCP tools. It's meant to
be studied, adapted, and used as a reference for building your own documentation MCP servers — starting with the JDK SDK
and growing into Spring Boot's more complex documentation ecosystem.

## Quick Start

### Build

```bash
mvn clean package
```

### Run

```bash
java -jar target/jaidoc-1.0.0.jar
```

### Embedding Model

The app uses a local ONNX transformer model for semantic search (vector embeddings). The model is not tracked in Git —
download it first:

```bash
# Download with defaults (project onnx/ directory)
.\scripts\download-onnx-transformer-model.ps1  # Windows
# or
./scripts/download-onnx-transformer-model.sh   # Linux/macOS
```

The script will ask which model and variant to download. To download a specific variant, pass the arguments directly:

```bash
.\scripts\download-onnx-transformer-model.ps1 multilingual-e5-small model
```

See [onnx/TRANSFORMER.md](onnx/TRANSFORMER.md) for available models, variants, and configuration options.

### Embedding Model — Test Which Variant Fits Your Hardware

> **⚠️ Important: Ingestion can take a long time on CPU.**
>
> The ingestion pipeline generates vector embeddings for every documentation chunk. With the **CPU-only model**, this
> process can take **more than 160 minutes** on the first run. A single JDK version generates at least **500 MB** of
> data in the database during ingestion.

The FP16 base model (`model.onnx`) runs significantly faster on CPU than the quantized INT8 variant
(`model_qint8_avx512_vnni.onnx`), despite being larger. On Intel Core Ultra 9 275HX hardware, the FP16 model
delivers noticeably better throughput. However, performance varies by CPU — **test both variants on your machine** to
see which gives you the best results:

```powershell
# Use the FP16 base model (larger file, faster on many CPUs)
$env:AI_TRANSFORMER_ONNX = "./onnx/model.onnx"

# Use the quantized INT8 model (smaller file, may be faster on some CPUs)
$env:AI_TRANSFORMER_ONNX = "./onnx/model_qint8_avx512_vnni.onnx"
```

The default is `model.onnx` (FP16), but override it with the `AI_TRANSFORMER_ONNX` environment variable to try the
other variant.

> **Crucial: the same model must be used for both ingestion and search.** Ingesting with one variant and searching with
> another produces incompatible embeddings — the embeddings are tied to the specific model, not the model family.
> However, **different model families** (e.g., `multilingual-e5-small` vs. `multilingual-e5-base`) also produce
> incompatible embeddings and cannot be mixed.

For full details, see [onnx/TRANSFORMER.md — CPU Inference](onnx/TRANSFORMER.md#cpu-inference).

## Example Queries

The MCP server exposes the following query capabilities through its tools:

- **`searchJavadoc(version, query, topK)`** — Semantic search of JDK API documentation using vector kNN embeddings.
  Returns chunks ranked by relevance, including kind (class/method/field), signature, and description.
- **`listVersions()`** — Lists available JDK versions whose documentation has been ingested.
- **`startDocGeneration(jdkVersion, jdkDistribution)`** — Start an async JDK documentation generation pipeline.
- **`getDocGenerationProgress(taskId)`** — Poll the status of a doc generation task.
- **`startIngest(jdkVersion, jdkDistribution)`** — Start an async JDK documentation ingestion pipeline.
- **`getIngestProgress(taskId)`** — Poll the status of an ingest task.

### Example: Semantic search for JDK API

You can ask the AI model: *"How do I read a file with NIO?"* and the model will call
`searchJavadoc("25", "read file NIO", 5)`
which returns the most relevant `java.nio.file.Files` documentation chunks — including the `readAllLines` method
signature
and description.

### Ingesting documentation

Before searching, documentation must be ingested into the database:

- **Doclet pipeline**: Run `JsonDoclet` on the JDK source to produce JSON Javadoc, then chunk the content via
  `ChunkWriter`, generate embeddings with the ONNX transformer model, and store chunks and elements in the database via
  the service layer. The `JdkVersionRepository` tracks which versions have been processed.

The ingest is idempotent — re-ingesting a version replaces any prior ingestion.

MCP tools `startIngest()` and `getIngestProgress()` allow triggering and polling the ingestion pipeline directly from
the AI model, without needing to run the server CLI.

## How It Works

### The Doclet Pipeline

The JDK doesn't ship its Javadoc as JSON, so we need to generate it from the source. JAIDoc handles this entirely:

1. **Download / Extract** — Fetch the official JDK source for a given version.
2. **Javadoc Serialization** — Run a custom doclet (`JsonDoclet`) on the JDK source to produce structured JSON directly,
   extracting class signatures, method descriptions, parameters, return types, and annotations in a format optimized for
   LLM comprehension.
3. **Vector Indexing** — Embed the JSON data with a local ONNX transformer model (FP16 base model,
   multilingual-e5-small)
   and index it into SQLite + Hibernate Search/Lucene for semantic search.
4. **MCP Tools Exposure** — Register MCP tools (`searchJavadoc`, `listVersions`, `startIngest`, `getIngestProgress`,
   `startDocGeneration`, `getDocGenerationProgress`) that allow AI models to query by semantic similarity, list
   versions, or trigger ingestion pipelines.

This pipeline is modular and version-aware: each JDK version gets its own ingestion run, and the database stores them
separately so users can query documentation for any supported version.

## Roadmap

- **Phase 1** ✅ JDK documentation ingestion and database layer; semantic search; MCP tools (`searchJavadoc`,
  `listVersions`, `startIngest`, `getIngestProgress`, `startDocGeneration`, `getDocGenerationProgress`)
- **Phase 2** Spring Boot ingestion: adoc parsing, migration guides, how-to guides, and structured MCP tools
- **Phase 3** Spring Framework API docs: annotations, generics, cross-references

### Phase 2: Spring Boot Integration

Spring Boot documentation is structured around AsciiDoc (`.adoc`) files — migration guides, how-to guides, and
reference documentation. Unlike JDK Javadoc (which a custom doclet can serialize to JSON), adoc requires a different
ingestion pipeline:

1. **adoc Parsing** — Extract sections, subsections, cross-references, and code examples from Spring Boot's adoc source
2. **Section Organization** — Structure the parsed content hierarchically so MCP tools can query by section, not just by
   keyword
3. **Migration Guide Tracking** — Preserve version-to-version migration paths so queries like "what changed in 3.4"
   return the relevant migration section
4. **How-To Expose** — Register MCP tools that let AI models query "how to do X" by matching natural language to adoc
   section titles and content
5. **Community Example** — Document the ingestion approach so others can replicate it for their own documentation
   ecosystems

This is where the real complexity lives: a much larger MCP schema, complex cross-references, and versioned migration
guides — building on the foundation established by the JDK integration.

## Project Structure

The repository is organized into distinct workspaces, each with a specific purpose:

| Directory        | Purpose                                                                                                                  |
|------------------|--------------------------------------------------------------------------------------------------------------------------|
| `src/`           | Java source code — `main/` for the application, `test/` for unit and integration tests                                   |
| `data/`          | JDK source and JSON documentation — versioned (one directory per JDK version), generated by the doclet pipeline          |
| `documentation/` | Deep-dive technical docs — architecture, database, MCP, security, testing, etc.                                          |
| `features/`      | Feature workspaces — planning context for ongoing feature development (see [features/FEATURES.md](features/FEATURES.md)) |
| `blackbook/`     | Dev log — dated notes and decisions from the developer                                                                   |
| `onnx/`          | Local AI models — ONNX embedding model and tokenizer used for semantic search                                            |
| `doclet/`        | Build output — the doclet JAR produced by Maven                                                                          |
| `assembly/`      | Maven assembly descriptor — packaging configuration for the doclet JAR                                                   |
| `request/`       | IntelliJ HTTP client — `mcp-tools.http` and environment config for manual MCP tool testing                               |

## Architecture

```mermaid
graph LR
    ai["🤖 AI / LLM"]
    server["🖥️ JAIDoc Server<br/>MCP Protocol / streamable"]
    db["📊 SQLite + Lucene<br/>JDK · Spring · …"]
    jdkdocs["📄 JDK Docs<br/>Javadoc JSON"]
    sbdocs["📄 Spring Boot Docs<br/>adoc · Migration · How-To"]
    ai <-->|" MCP (streamable) "| server
    server -->|" Search / Ingest "| jdkdocs
    server -->|" Ingest (adoc, planned) "| sbdocs
    server -->|" Search "| db
```

## Philosophy

> *"The best documentation is the kind that an AI can consume in a structured, semantic way — without sacrificing
readability for humans. And the best AI is the kind you run locally, on your own hardware."*

JAIDoc doesn't aim to replace human documentation. It complements it by providing AI assistants with a reliable,
indexed, and searchable source so they can generate more accurate technical answers. The key insight: **documentation
should be machine-readable AND human-readable**. The Doclet output is structured JSON (machine-first), but it faithfully
preserves all the original Javadoc content — the human-readable body, examples, and cross-references are all there for
the LLM to ground its responses on.

Equally important: **this stack runs locally**. No cloud APIs, no API keys, no data leaving your machine. The Llama.cpp
Server + local models provide the same capabilities as any cloud-based AI — and you control the models, the data, and
the privacy.

## Getting Help

- **Doclet internals** — [`documentation/DOCLET.md`](documentation/DOCLET.md)
- **JDK documentation data** — [`documentation/JDK-DATA.md`](documentation/JDK-DATA.md)
- **Database** — [`documentation/DATABASE.md`](documentation/DATABASE.md)
- **AI models** — [`documentation/AI-MODELS.md`](documentation/AI-MODELS.md)
- **MCP setup** — [`documentation/MCP.md`](documentation/MCP.md)
- **Project structure** — [`documentation/STRUCTURE.md`](documentation/STRUCTURE.md)
- **Jackson configuration** — [`documentation/JACKSON.md`](documentation/JACKSON.md)
- **ONNX embedding model** — [`onnx/TRANSFORMER.md`](onnx/TRANSFORMER.md)
- **Development log** — [`blackbook/BLACKBOOK.md`](blackbook/BLACKBOOK.md)

## Contributing

Contributions are welcome. Whether you want to extend the Doclet to handle new JDK features, add Spring Boot adoc
parsing, add support for additional ecosystems, or improve the MCP tools — please open an issue or submit a PR.

## License

[MIT License](LICENSE)
