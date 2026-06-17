# JAIDoc

[![Java](https://img.shields.io/badge/Java-25-red.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9.15-blue.svg)](https://maven.apache.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

JAIDoc is an **exercise in creating a Model Context Protocol (MCP) server** that makes JDK and Spring Boot documentation
searchable and consumable by AI models. It's a practical example of how to bridge the gap between traditional technical
documentation and AI-driven development workflows — entirely with local AI.

## Why JAIDoc?

The official Java and Spring Boot documentation is vast, well-maintained, and constantly updated — but it's locked
behind HTML pages, versioned separately, and not queryable by AI models in context. When you're coding and need to
verify how a method works or what a class does, you have to leave your IDE, search Google, navigate to the docs site,
and find the right version.

JAIDoc is also an **example for the community** on how to organize, track, and expose technical documentation through
MCP tools. Currently focused on the JDK SDK as a foundation, the project aims to grow into Spring Boot — where
documentation is far more complex (migration guides, how-to guides, AsciiDoc formats, cross-references) — and serve as
a reference for building your own documentation MCP servers.

JAIDoc solves this by letting the local AI model (Qwen 3.6, Gemma-4, or QWOPUS — running on your machine) answer these
questions directly, without relying on cloud APIs or sending code context to third-party services.

It does this by:

1. **Converting** official documentation into structured JSON via a Java Doclet
2. **Indexing** it for semantic search (vector embeddings)
3. **Exposing** it through the MCP protocol so AI models can query it directly

This project demonstrates the full stack: doclet → JSON → vector DB → MCP tools. It's meant to be studied, adapted, and
used as a reference for building your own documentation MCP servers — starting with the JDK SDK and growing into Spring
Boot's more complex documentation ecosystem.

## Quick Start

### Build

```bash
mvn clean package
```

### Run

```bash
java -jar target/jaidoc-0.1.0.jar
```

### Embedding Model

The app uses a local ONNX transformer model for semantic search (vector embeddings). The model is not tracked in Git —
download it first:

The script will ask which model and variant to download. See [onnx/TRANSFORMER.md](onnx/TRANSFORMER.md) for available
models, variants, and configuration options.

## Example Queries

Once connected, the MCP server exposes tools for querying documentation. Here's what you can do:

- **Search by class name** — Find a specific class and its members
- **Search by method signature** — Look up a method's parameters, return type, and description
- **Keyword search** — Search across all documentation for a term
- **Semantic search** — Find documentation relevant to a natural language question

### Example: Find how to create an HTTP client

You can ask the AI model: *"How do I create a WebClient in Spring Boot?"* and the model will query the MCP server for
Spring Boot documentation, returning the precise API reference with parameters and usage examples.

### Example: Find migration changes (future)

When Spring Boot documentation is ingested, you'll be able to ask: *"What changed in the migration from 3.3 to 3.4?"*
and the model will return the relevant migration guide section with version-specific changes.

## How It Works

### The Doclet Pipeline

The JDK doesn't ship its Javadoc as JSON, so we need to generate it from the source. JAIDoc handles this entirely:

1. **Download / Extract** — Fetch the official JDK source for a given version.
2. **Javadoc Serialization** — Run a custom doclet (`JsonDoclet`) on the JDK source to produce structured JSON directly,
   extracting class signatures, method descriptions, parameters, return types, and annotations in a format optimized for
   LLM comprehension.
3. **Vector Indexing** — Embed and index the JSON data into a vector database for semantic search.
4. **MCP Tools Exposure** — Register MCP tools that allow AI models to query by class name, method signature, keyword
   search, or semantic similarity.

This pipeline is modular and version-aware: each JDK version gets its own ingestion run, and the vector DB stores them
separately so users can query documentation for any supported version.

### The Spring Boot Pipeline (planned)

Spring Boot documentation goes beyond API reference — it includes migration guides, how-to guides, and AsciiDoc
(`.adoc`) formats with richer structure than Javadoc. Ingestion will require a different approach: parsing adoc files,
extracting sections, preserving cross-references, and organizing the data so MCP tools can expose structured queries (
e.g., "what changed in Spring Boot 3.4 migration", "how to configure a custom bean").

This pipeline will also serve as an example for the community on how to organize, track, and expose complex
documentation formats through MCP — a foundation that can be extrapolated to other ecosystems beyond Spring Boot.

Currently this work starts with the Java SDK as a foundation, then extrapolates to Spring Boot — which is where the real
complexity lives (huge MCP schema, complex cross-references, versioned migration guides).

## Architecture

```mermaid
graph LR
    ai["🤖 AI / LLM"]
    server["🖥️ JAIDoc Server<br/>MCP Protocol / stdio"]
    vdb["📊 Vector DB<br/>JDK · Spring · …"]
    jdkdocs["📄 JDK Docs<br/>Javadoc JSON"]
    sbdocs["📄 Spring Boot Docs<br/>adoc · Migration · How-To"]
    ai <-->|" MCP "| server
    server -->|" Search "| vdb
    server -->|" Ingest (Doclet) "| jdkdocs
    server -->|" Ingest (adoc) "| sbdocs
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

[Apache License 2.0](LICENSE)
