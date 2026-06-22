# Features

Feature workspaces for JAIDoc. A feature workspace is a **context bundle**: it gathers all the
documentation an AI needs to produce an implementation plan — purpose, scope, data flow, mock data,
and notes. The implementation **plan itself is independent and is not stored here**; a feature only
provides the grounded context that makes a good plan possible.

## Index

| Feature                          | Status     | Description                                    |
|----------------------------------|------------|-------------------------------------------------|
| [Example](example/)              | Template   | Template feature workspace                      |
| [Data Models](feature-data-models/)       | Implemented | JPA entities for JDK documentation storage     |
| [Embedding Converter](feature-embedding-converter/) | Implemented | float[] ↔ byte[] BLOB converter for SQLite |
| [ZIP Helper](feature-zip-helper/)         | Implemented | ZIP entry lookup with version-prefixed paths |
| [Model Classes](feature-model-classes/)   | Implemented | Enums and DTOs for domain concepts           |
| [JDK Distribution Download](feature-jdk-distribution-download/) | Implemented | Adoptium/Temurin API integration |
| [JSON Doclet](feature-json-doclet/)       | Implemented | Custom Javadoc-to-JSON doclet              |
| [Documentation Service](feature-documentation-service/) | Implemented | JDK doc generation pipeline |
| [Embedding Service](feature-embedding-service/) | Implemented | Spring AI embedding wrapper          |
| [Database Ingestion](feature-database-ingestion/) | Implemented | ZIP → database ingestion         |
| [Auto-Discovery Ingestion](feature-auto-discovery-ingestion/) | Implemented | Startup auto-ingest for ZIPs |
| [Semantic Search](feature-semantic-search/) | Implemented | kNN vector search over chunks        |
| [MCP Tools](feature-mcp-tools/)           | Implemented | MCP tools for JDK/Spring Boot docs |
| [MCP Configuration](feature-mcp-configuration/) | Implemented | ToolCallbackProvider registration |
| [Application Bootstrap](feature-application-bootstrap/) | Implemented | Main class + Jackson mapper config |
| [Repository Layer](feature-repository-layer/) | Implemented | JPA repositories for CRUD queries |
| [Configuration](feature-configuration/)   | Implemented | All YAML config files and keys     |

## Feature Layout

Each feature lives in its own kebab-case directory under `features/`:

| File                  | Role                                                               | Required |
|-----------------------|--------------------------------------------------------------------|----------|
| `README.md`           | Entry point — feature spec (context, scope, implementation notes)  | Yes      |
| `data-flow.md`        | End-to-end data flow: sequence, data models, error states          | Yes      |
| `mock-requests.json`  | Mock request/response pairs aligned with the API contract          | Yes      |
| `notes.md`            | Observations, decisions, and open questions about the workspace    | No       |
| `scripts/validate.sh` | Checks the workspace has the required files and frontmatter         | No       |

`README.md` carries YAML frontmatter (`name`, `status`, `date`) so the feature's status is trackable.

## Status

Status describes the **feature**, not a plan. A feature is only ever a template, not yet built, or built — there is no
"in progress" state to keep updating.

| Status            | Meaning                                  | AI Action                                              |
|-------------------|------------------------------------------|--------------------------------------------------------|
| `template`        | A reference example — not a real feature | **Never implement** — use as a structural guide only   |
| `not implemented` | Documented but not yet built             | Read as context to design and build the implementation |
| `implemented`     | Complete and in the codebase             | Reference for context; don't duplicate existing work   |

**Templates are not real features.** The `example/` feature describes the structure a real feature should have — not
a feature to implement. When creating a new feature, copy the template, rename it, and fill in the real content.

## Naming Conventions

- Feature directories use kebab-case: `feature-slug`
- Prefix with `feature-` for new features, `fix-` for bug fixes
- Multi-word names use kebab-case: `jdk-docs-ingestion`, `auth-token-refresh`
