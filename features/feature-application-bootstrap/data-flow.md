# Data Flow

## Overview

The application bootstrap configures the main application class with a unique APP_ID and BufferingApplicationStartup,
and registers Jackson 3 mapper customizers.

## Sequence Diagram

```
JAIDoc.main()
    │
    ├──→ Generate APP_ID (UUID)
    │   └──→ System.setProperty("APP_ID", appId)
    │
    ├──→ Configure BufferingApplicationStartup
    │   └──→ Buffer size: 2048
    │   └──→ Filter: spring.beans.instantiate
    │
    └──→ Run Spring Boot application

ObjectMapperConfiguration
    │
    ├──→ JsonMapperBuilderCustomizer bean
    │   └──→ Disable DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS
    │   └──→ Enable DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
    │
    ├──→ XmlMapperBuilderCustomizer bean
    │   └──→ Same settings as above
    │
    └──→ YAMLMapper bean
        └──→ Same settings as above
```

## Data Models

### Input

None — the bootstrap runs automatically on application startup.

### Output

```
APP_ID: <UUID> — unique identifier for this application instance
BufferingApplicationStartup — startup metrics buffer (2048 steps, filter: spring.beans.instantiate)
JsonMapperBuilderCustomizer — applied to all JsonMapper beans
XmlMapperBuilderCustomizer — applied to all XmlMapper beans
YAMLMapper — standalone YAML mapper bean
```

## Error States

- None — the bootstrap doesn't throw exceptions; errors are handled by Spring Boot
