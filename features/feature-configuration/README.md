---
name: feature-configuration
status: implemented
date: 2026-06-21
---

# Configuration

> All YAML configuration files for the JAIDoc application, organized into profile-specific modules.

## Context

The JAIDoc application uses a modular configuration approach with 9 YAML configuration files imported by the main
`application.yaml`. Each file groups related configuration keys together:

1. **application.yaml** — Main config, profile imports, virtual threads enablement
2. **db-configuration.yml** — SQLite datasource, Hibernate Search index directory
3. **ai-configuration.yml** — ONNX model and tokenizer URIs for the embedding model
4. **mcp-configuration.yml** — MCP server settings (protocol, name)
5. **search-configuration.yml** — Hibernate Search index config
6. **ingest-configuration.yml** — Ingestion enable/disable settings
7. **logging-configuration.yml** — Logback rolling policy and log file paths
8. **springdoc-configuration.yml** — Swagger UI and API docs settings
9. **actuator-configuration.yml** — Actuator endpoint settings

Most configuration values have environment variable overrides with sensible defaults, enabling deployment flexibility
without modifying the YAML files.

## Feature Inputs

- `src/main/resources/application.yaml` — Main config, profile imports
- `src/main/resources/configurations/db-configuration.yml` — SQLite datasource, Hibernate Search index dir
- `src/main/resources/configurations/ai-configuration.yml` — ONNX model and tokenizer URIs
- `src/main/resources/configurations/mcp-configuration.yml` — MCP server settings
- `src/main/resources/configurations/search-configuration.yml` — Hibernate Search index config
- `src/main/resources/configurations/ingest-configuration.yml` — Ingestion enable/disable
- `src/main/resources/configurations/logging-configuration.yml` — Logback rolling policy
- `src/main/resources/configurations/springdoc-configuration.yml` — Swagger UI settings
- `src/main/resources/configurations/actuator-configuration.yml` — Actuator endpoint settings

## Scope

In: All configuration keys and their defaults, environment variable overrides, configuration hierarchy
Out: Application logic that uses these configuration values

## Implementation

### Architecture

The configuration is organized into 9 YAML files imported by `application.yaml`. Each file groups related configuration
keys together. Most values have environment variable overrides with the pattern `${VAR:default}`.

### Files

- `src/main/resources/application.yaml` — Main config, profile imports
- `src/main/resources/configurations/db-configuration.yml` — SQLite datasource, Hibernate Search index dir
- `src/main/resources/configurations/ai-configuration.yml` — ONNX model and tokenizer URIs
- `src/main/resources/configurations/mcp-configuration.yml` — MCP server settings
- `src/main/resources/configurations/search-configuration.yml` — Hibernate Search index config
- `src/main/resources/configurations/ingest-configuration.yml` — Ingestion enable/disable
- `src/main/resources/configurations/logging-configuration.yml` — Logback rolling policy
- `src/main/resources/configurations/springdoc-configuration.yml` — Swagger UI settings
- `src/main/resources/configurations/actuator-configuration.yml` — Actuator endpoint settings

### Data Flow

```
application.yaml
    │
    └──→ spring.config.import:
            ├── configurations/actuator-configuration.yml
            ├── configurations/ai-configuration.yml
            ├── configurations/db-configuration.yml
            ├── configurations/documentation-configuration.yml
            ├── configurations/ingest-configuration.yml
            ├── configurations/logging-configuration.yml
            ├── configurations/mcp-configuration.yml
            ├── configurations/search-configuration.yml
            └── configurations/springdoc-configuration.yml
```

### Configuration Keys

| Key                                | Default                                  | Description                      |
|------------------------------------|------------------------------------------|----------------------------------|
| `DB_USERNAME`                      | jaidoc                                   | Database username                |
| `DB_PASSWORD`                      | (none)                                   | Database password                |
| `DB_SHOW_SQL`                      | false                                    | Show SQL in logs                 |
| `AI_TRANSFORMER_ONNX`              | file:./onnx/model_qint8_avx512_vnni.onnx | ONNX model URI                   |
| `AI_TRANSFORMER_TOKENIZER`         | file:./onnx/tokenizer.json               | Tokenizer JSON path              |
| `SEARCH_INDEX_DIR`                 | ./jaidoc-index                           | Hibernate Search index directory |
| `INGEST_ENABLED`                   | true                                     | Enable ingestion                 |
| `INGEST_AUTO_SCAN`                 | true                                     | Enable auto-scan on startup      |
| `LOGGING_MAX_FILE_SIZE`            | 10MB                                     | Max log file size                |
| `LOGGING_MAX_HISTORY_DAYS`         | 7                                        | Max log history days             |
| `LOGGING_MAX_BACKUP_FILES`         | 5                                        | Max backup files                 |
| `LOGGING_PATH_DIRECTORY`           | ./logs                                   | Log directory                    |
| `SPRINGDOC_API_DOCS_ENABLED`       | true                                     | Enable API docs                  |
| `SPRINGDOC_SWAGGER_UI_ENABLED`     | true                                     | Enable Swagger UI                |
| `ACTUATOR_HEALTH_SHOW_DETAILS`     | ALWAYS                                   | Health details visibility        |
| `ACTUATOR_LOGGERS_ACCESS`          | READ_ONLY                                | Loggers endpoint access          |
| `ACTUATOR_ENV_SHOW_VALUES`         | NEVER                                    | Env endpoint values              |
| `ACTUATOR_CONFIGPROPS_SHOW_VALUES` | NEVER                                    | Configprops endpoint values      |
| `ACTUATOR_ENDPOINTS_WEB_EXPOSE`    | *                                        | Web endpoints to expose          |
| `ACTUATOR_ENDPOINTS_EXPOSE`        | (none)                                   | JMX endpoints to expose          |
| `ACTUATOR_INFO_BUILD_ENABLED`      | true                                     | Build info                       |
| `ACTUATOR_INFO_JAVA_ENABLED`       | true                                     | Java info                        |
| `JDK_DIST_DOWNLOAD_DIR`            | ./jdk-distributions                      | JDK distribution cache           |
| `DATA_DIR`                         | ./data                                   | Documentation data directory     |
| `DOCLET_WORK_DIR`                  | ./jdk-doc-workspace                      | Doclet working directory         |
| `DOCLET_JAR_DIR`                   | ./doclet                                 | Doclet JAR directory             |
| `DOCLET_JAVADOC_HOME`              | (empty = running JDK)                    | Javadoc JDK home                 |
| `DOCLET_MODULES`                   | (empty = all modules)                    | Modules to document              |

## Tests

No dedicated unit tests exist for the configuration. The configuration is implicitly tested through the integration
tests.

## Notes

- The ONNX model URI must use the `file:` scheme — bare Windows paths like `C:\...\onnx\model.onnx` will not work (
  Spring AI tries to parse them as HTTP URLs). See `onnx/TRANSFORMER.md` for details.
- The SQLite password (`DB_PASSWORD`) has no default — it must be provided via environment variable for production
  deployments.
- The `spring.threads.virtual.enabled=true` enables virtual threads for all async operations (doclet execution,
  download, etc.).
