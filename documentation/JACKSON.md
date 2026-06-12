# Jackson Configuration

## Customizer Pattern

The app uses Spring Boot's `*MapperBuilderCustomizer` beans to apply common configuration across all Jackson mappers:

```java

@Bean
public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() { ...}

@Bean
public XmlMapperBuilderCustomizer xmlMapperBuilderCustomizer() { ...}
```

Both customizers apply the same two settings:

- **Disable** `WRITE_DATES_AS_TIMESTAMPS` — dates are serialized as ISO strings, not epoch milliseconds
- **Enable** `FAIL_ON_UNKNOWN_PROPERTIES` — fail on deserialization when a JSON field has no matching Java field

## YAMLMapper

A dedicated `YAMLMapper` bean is also created with the same settings. This is needed because Spring Boot's YAML support
uses its own mapper instance, not the customizers.

```java

@Bean
public YAMLMapper yamlMapper() { ...}
```

## Convention

When adding new Jackson mappers (e.g., for a new data format), either:

1. Add a `*MapperBuilderCustomizer` bean — the customizers apply automatically to all standard Jackson mappers
2. Create a dedicated mapper bean with the same settings (like `YAMLMapper`)
