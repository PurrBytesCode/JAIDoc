# JAIDoc

[![Java](https://img.shields.io/badge/Java-25-red.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9.15-blue.svg)](https://maven.apache.org/)

JAIDoc is a **Model Context Protocol (MCP) server** designed to expose technical documentation in a structured format,
optimized for consumption by AI models. Its primary goal is to help generate and query API, class, and method
documentation specifically tailored for language model comprehension.

## Objective

The project is born from a simple yet powerful idea: **establish a standard for what AI-ready documentation should look
like**. It's not just about indexing information — it's about creating a structured source that LLMs can query
efficiently to answer technical questions about Java APIs and ecosystems.

## How It Works

1. **Documentation Ingestion** — Official documentation (JDK, Spring Framework) is fetched in JSON format optimized for
   AI consumption.
2. **Vector Organization** — The documentation is indexed into a vector database, enabling semantic search and
   contextual retrieval.
3. **MCP Exposure** — AI models can query the documentation through the MCP protocol, obtaining precise and
   contextualized technical answers.

## The Ingestion Pipeline

The JDK doesn't ship its Javadoc as JSON, so we need a way to generate it ourselves. JAIDoc handles this entirely — step
by step:

1. **Download / Extract** — Fetch the official JDK source or binary distribution for a given version.
2. **Javadoc Generation** — Run `javadoc` on the JDK source to produce HTML Javadoc (or parse existing HTML if sources
   aren't available).
3. **HTML → JSON Conversion** — Transform the generated HTML into structured JSON, extracting class signatures, method
   descriptions, parameters, return types, and annotations in a format optimized for LLM comprehension.
4. **Vector Indexing** — Embed and index the JSON data into a vector database for semantic search.
5. **MCP Tools Exposure** — Register MCP tools that allow AI models to query by class name, method signature, keyword
   search, or semantic similarity.

This pipeline is modular and version-aware: each JDK version gets its own ingestion run, and the vector DB stores them
separately so users can query documentation for any supported version. The same approach will be reused for Spring
Framework, where we'll parse Spring's API docs (which are more complex due to annotations, generics, and
cross-references).

## Scope

The project is designed to grow incrementally:

- **Phase 1 (current)** — JDK documentation. We start with the core Java ecosystem, covering the platform's main APIs.
- **Phase 2** — Spring Framework documentation. A larger and more complex ecosystem that will be added gradually, taking
  advantage of the project's flexible architecture.

Each phase supports multiple versions, allowing selective querying of documentation from different releases.

## Architecture

```
┌─────────────┐     MCP      ┌──────────┐   Semantic    ┌──────────────┐
│   AI / LLM  │ ◄────────► │ JAIDoc   │ ◄────────────► │ Vector DB    │
│             │   Protocol  │  Server  │   Search        │              │
└─────────────┘            └──────────┘                 └──────────────┘
                                    ▲
                                    │ JSON Ingestion
                              ┌──────────┐
                              │ Docs     │
                              │ (JDK,   │
                              │ Spring)  │
                              └──────────┘
```

## Build

```bash
mvn clean package
```

## Run

```bash
java -jar target/jaidoc-0.1.0.jar
```

## Philosophy

> *"The best documentation is the kind that an AI can consume in a structured, semantic way."*

JAIDoc doesn't aim to replace human documentation — it complements it by providing AI assistants with a reliable,
indexed, and searchable source so they can generate more accurate technical answers.
