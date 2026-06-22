# Data Flow

## Overview

The configuration is organized into 9 YAML files imported by the main application.yaml. Each file groups related configuration keys together, with environment variable overrides for deployment flexibility.

## Sequence Diagram

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

## Data Models

### Input (Environment Variables)

```
DB_USERNAME=jaidoc
DB_PASSWORD=secret
AI_TRANSFORMER_ONNX=file:./onnx/model.onnx
AI_TRANSFORMER_TOKENIZER=file:./onnx/tokenizer.json
SEARCH_INDEX_DIR=./jaidoc-index
INGEST_ENABLED=true
INGEST_AUTO_SCAN=true
LOGGING_MAX_FILE_SIZE=10MB
LOGGING_MAX_HISTORY_DAYS=7
LOGGING_MAX_BACKUP_FILES=5
LOGGING_PATH_DIRECTORY=./logs
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
ACTUATOR_HEALTH_SHOW_DETAILS=ALWAYS
ACTUATOR_LOGGERS_ACCESS=READ_ONLY
ACTUATOR_ENV_SHOW_VALUES=NEVER
ACTUATOR_CONFIGPROPS_SHOW_VALUES=NEVER
ACTUATOR_ENDPOINTS_WEB_EXPOSE=*
ACTUATOR_INFO_BUILD_ENABLED=true
ACTUATOR_INFO_JAVA_ENABLED=true
JDK_DIST_DOWNLOAD_DIR=./jdk-distributions
DATA_DIR=./data
DOCLET_WORK_DIR=./jdk-doc-workspace
DOCLET_JAR_DIR=./doclet
DOCLET_JAVADOC_HOME=
DOCLET_MODULES=
```

### Output (Configuration)

```
All configuration values are available to Spring Boot as application properties,
accessible via @Value annotations and Spring Boot's configuration binding.
```

## Error States

- `IllegalArgumentException` — If an environment variable override uses an invalid value (e.g., non-numeric for a numeric field)
- `IOException` — If the ONNX model URI points to a non-existent file
