# Data Flow

## Overview

The repository layer provides JPA data access for JDK documentation entities. Two repositories have custom queries: one
to list available versions and one to find elements by version and qualified ID.

## Sequence Diagram

```
JdkVersionRepository.findAllVersionStringsOrderByMajorDesc()
    │
    └──→ JPQL: SELECT v.version FROM JdkVersion v
                 WHERE v.status = 'READY'
                 ORDER BY v.major DESC, v.minor DESC, v.security DESC
            └──→ List<String>

JdkDocElementRepository.findByJdkVersionAndQualifiedId(jdkVersion, ownerId)
    │
    └──→ Derived query: findByJdkVersionAndQualifiedId
            └──→ Optional<JdkDocElement>
```

## Data Models

### findAllVersionStringsOrderByMajorDesc Output

```json
["25.0.3", "21.0.11", "17.0.13"]
```

### findByJdkVersionAndQualifiedId Input

```
JdkVersion — the version entity
String qualifiedId — e.g., "java.io.BufferedInputStream"
```

### findByJdkVersionAndQualifiedId Output

```
Optional<JdkDocElement> — the parent element for a chunk
```

## Error States

- None — the repositories don't throw exceptions; Spring Data JPA handles errors internally
