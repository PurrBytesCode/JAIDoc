# Project Structure

## Top-level layout

```
JAIDoc/
├── AGENTS.md                        # AI agent guidelines (this repo's rules)
├── README.md                        # Project overview
├── pom.xml                          # Maven build (Spring Boot 4.0.6, Java 25)
├── mvnw / mvnw.cmd                  # Maven wrapper
├── .mcp.json                        # MCP server configuration
├── documentation/                   # Deep-dive docs
│   ├── DOCLET.md                    # Doclet architecture, CLI options, output format
│   ├── JACKSON.md                   # Jackson customizer pattern
│   ├── JDK-SOURCES.md               # JDK source downloader
│   ├── MCP.md                       # MCP server setup
│   ├── SECURITY.md                  # Actuator restrictions, logging paths
│   └── STRUCTURE.md                 # This file
├── plans/                           # Implementation plans (see AGENTS.md)
└── src/
    ├── main/
    │   ├── java/com/purrbyte/ai/
    │   │   ├── JAIDoc.java          # Spring Boot entry point
    │   │   ├── configuration/       # JSON serialization config
    │   │   ├── doclet/              # JSON Javadoc serialization
    │   │   ├── mcp/                 # MCP server (stub)
    │   │   ├── service/             # Business logic
    │   │   └── util/                # Shared utilities
    │   └── resources/
    │       ├── application.yaml     # Main config
    │       └── configurations/      # Profile YAMLs
    └── test/
```

## Configuration hierarchy

`application.yaml` is the entry point; it imports 5 profile YAMLs:

1. **actuator-configuration.yml** — Actuator endpoints, health, loggers, env, configprops
2. **documentation-configuration.yml** — JDK source download directory
3. **logging-configuration.yml** — Logback rolling policy, log file path
4. **mcp-configuration.yml** — Spring AI MCP server (name, streamable protocol, stdio)
5. **springdoc-configuration.yml** — OpenAPI/Swagger UI toggles

All values use environment variable placeholders for flexibility.

## Build output

Maven produces a Spring Boot fat JAR in `target/`.

## Maintenance

Keep this file compact — group related items under one line, only expand when something is truly independent.
