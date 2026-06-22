---
name: feature-application-bootstrap
status: implemented
date: 2026-06-21
---

# Application Bootstrap

> Main application class with startup configuration, and JSON mapper customizer for Jackson 3 serialization.

## Context

This feature defines two bootstrap components:

1. **JAIDoc** — The main application class. It generates a unique `APP_ID` (UUID) for each instance, sets it as a system
   property, and configures a `BufferingApplicationStartup` that captures startup metrics. The buffer size is 2048 and
   it filters to only capture `spring.beans.instantiate` startup steps.

2. **ObjectMapperConfiguration** — Configures Jackson 3 JSON/YAML mappers with two settings: disable timestamp writing
   for dates (ISO-8601 format instead), and fail on unknown properties during deserialization. These customizers are
   applied automatically to all `JsonMapper` and `XmlMapper` beans created by Spring Boot.

## Feature Inputs

- `src/main/java/com/purrbyte/ai/JAIDoc.java` — Main application class
- `src/main/java/com/purrbyte/ai/configuration/ObjectMapperConfiguration.java` — Jackson 3 mapper configuration
- `src/test/java/com/purrbyte/ai/JAIDocTest.java` — Tests for the main class static methods

## Scope

In: APP_ID generation, BufferingApplicationStartup configuration, JsonMapper/XmlMapper customizers, YAML mapper bean
Out: Spring Boot auto-configuration (covered in Configuration)

## Implementation

### Architecture

The main class configures a `BufferingApplicationStartup` with a buffer of 2048 startup steps and a filter for
`spring.beans.instantiate`. This is used for monitoring Spring Boot startup performance.

The ObjectMapperConfiguration creates three beans:

- `JsonMapperBuilderCustomizer` — Applied to all `JsonMapper` beans
- `XmlMapperBuilderCustomizer` — Applied to all `XmlMapper` beans
- `YAMLMapper` — Standalone YAML mapper bean

### Files

- `src/main/java/com/purrbyte/ai/JAIDoc.java` — Main application class
- `src/main/java/com/purrbyte/ai/configuration/ObjectMapperConfiguration.java` — Jackson 3 mapper configuration

### Data Flow

```
JAIDoc.main()
    │
    ├──→ Generate APP_ID (UUID)
    │   └──→ System.setProperty("APP_ID", appId)
    │
    └──→ Configure BufferingApplicationStartup
            └──→ Buffer size: 2048
            └──→ Filter: spring.beans.instantiate
            └──→ Run Spring Boot application

ObjectMapperConfiguration
    │
    ├──→ JsonMapperBuilderCustomizer bean
    │   └──→ Applied to all JsonMapper beans:
    │       └──→ Disable DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS
    │       └──→ Enable DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
    │
    ├──→ XmlMapperBuilderCustomizer bean
    │   └──→ Same settings as above
    │
    └──→ YAMLMapper bean
        └──→ Same settings as above
```

### Configuration

- `src/main/java/com/purrbyte/ai/JAIDoc.java` — Hardcoded values: startupSize=2048, startupFilter="
  spring.beans.instantiate"

## Tests

- `src/test/java/com/purrbyte/ai/JAIDocTest.java` — Tests for the main class static methods:
    - `getStartupSize()` returns 2048
    - `getStartupFilter()` returns "spring.beans.instantiate"
    - `prepareStartup()` creates a BufferingApplicationStartup

## Notes

- The `APP_ID` is generated once per application instance and set as a system property — this can be used for request
  tracing or logging correlation.
- The `BufferingApplicationStartup` is configured to only capture `spring.beans.instantiate` startup steps — this limits
  the buffer usage since bean instantiation is the most expensive part of Spring Boot startup.
