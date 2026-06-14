# Test Architecture

## Test class hierarchy

```
BaseTest (abstract)
├── @Slf4j
├── @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
├── @ExtendWith(TimeExtension.class)
├── @ActiveProfiles({"test"})
├── @Tag(BaseTest.TAG_UNIT)
└── @Tag(BaseTest.TAG_INTEGRATION)

UnitTest extends BaseTest (abstract)
├── Constructor: builds JsonMapper manually using ObjectMapperConfiguration.customizer
├── protected final JsonMapper jsonMapper
└── Purpose: fast, isolated unit tests — NO Spring context

IntegrationTest extends BaseTest (abstract)
├── @SpringBootTest
├── @Autowired protected JsonMapper jsonMapper
└── Purpose: integration tests — need Spring context and bean wiring
```

## Design principles

### 1. No Spring in unit tests

`UnitTest` does **not** have `@SpringBootTest`. It builds `JsonMapper` manually in the constructor, applying the same
`JsonMapperBuilderCustomizer` that `ObjectMapperConfiguration` provides:

```java
public UnitTest() {
    JsonMapper.Builder builder = new JsonMapper.Builder(new JsonFactoryBuilder().build());
    new ObjectMapperConfiguration().jsonMapperBuilderCustomizer().customize(builder);
    this.jsonMapper = builder.build();
}
```

This means if someone modifies the customizer, the unit tests will **fail** — just as they would in production. The
manual instantiation is intentional; it keeps the test aligned with real configuration changes.

### 2. Spring beans in integration tests

`IntegrationTest` has `@SpringBootTest`, which starts the full application context. `JsonMapper` is injected via
`@Autowired` from the bean — no manual construction needed. This is the correct approach for integration tests that
depend on Spring wiring.

### 3. Test tags for pipeline selection

Test class hierarchy classes carry JUnit 5 `@Tag` annotations so the CI pipeline can run only unit or integration tests
independently. Tag constants are defined in `BaseTest`:

- **`BaseTest.TAG_UNIT`** — run with `mvn test -Dgroups=UNIT` or `<groups>UNIT</groups>` in surefire
- **`BaseTest.TAG_INTEGRATION`** — run with `mvn test -Dgroups=INTEGRATION` or `<groups>INTEGRATION</groups>`.
  Integration tests are skipped unless the property `test.integration.enabled=true` is set
  (via environment variable, system property, or `application-test.yaml`).

Concrete unit test classes reference `BaseTest.TAG_UNIT`; integration test classes reference `BaseTest.TAG_INTEGRATION`.
This prevents integration tests from breaking the CI when Spring context startup fails.

## Test base class annotations

| Annotation                                              | Purpose                                                        |
|---------------------------------------------------------|----------------------------------------------------------------|
| `@Slf4j`                                                | Lombok logger in all test classes                              |
| `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` | Respect `@Order` on test methods                               |
| `@ExtendWith(TimeExtension.class)`                      | Measure test execution time                                    |
| `@ActiveProfiles({"test"})`                             | Use the `test` Spring profile                                  |
| `@Tag(BaseTest.TAG_UNIT)`                               | **Only on `BaseTest`** — applies to unit test hierarchy        |
| `@Tag(BaseTest.TAG_INTEGRATION)`                        | **Only on `BaseTest`** — applies to integration test hierarchy |
| `@SpringBootTest`                                       | **Only on `IntegrationTest`** — starts full context            |

## Tag constants

Defined in `BaseTest` as public static final fields:

```java
public static final String TAG_UNIT = "UNIT";
public static final String TAG_INTEGRATION = "INTEGRATION";
```

All test classes reference these constants via `BaseTest.TAG_UNIT` / `BaseTest.TAG_INTEGRATION` — never as raw strings.
This ensures consistency and makes it easy to add new tag types in one place.

## JsonMapper configuration

Both `UnitTest` and `IntegrationTest` use the same two customizations:

- `WRITE_DATES_AS_TIMESTAMPS` disabled — dates serialize as ISO-8601 strings
- `FAIL_ON_UNKNOWN_PROPERTIES` enabled — fail-fast on unknown properties during deserialization

The `DateTimeFeature` and `DeserializationFeature` are imported from `tools.jackson.*`, the same Jackson implementation
used throughout the project.

## No warnings in test code

Test classes must compile **clean** — no compiler warnings. If a warning appears, fix it rather than ignoring it. Common
test-specific warnings to watch for:

- **Dead parameters**: Factory methods or constructors with parameters that are never used by any test (e.g. a hardcoded
  `false` always passed). If a parameter is always the same value, either remove it or create separate factory methods
  for each path (e.g. `createChunkWriter(...)` vs `createDocumentedOnlyChunkWriter(...)`).
- **Unused fields**: Instance fields that are only used by a few tests when most tests call static methods. Prefer local
  variables or inline construction.
- **`var` in tests**: Use explicit types instead of `var` — test code is documentation and `var` hides the type from
  readers.
- **Duplicate tests**: Two test methods with the same input and expected output are wasted maintenance cost.
- **Unused imports**: Remove any import that isn't directly referenced in the test file.

Warnings from dependencies (Lombok's Unsafe usage, Mockito's self-attachment, Spring Boot's BeanPostProcessorChecker)
are not test issues — they belong in the build infrastructure or dependency configuration, not in test code.
