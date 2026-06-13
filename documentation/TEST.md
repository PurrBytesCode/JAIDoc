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

`UnitTest` does **not** have `@SpringBootTest`. It builds `JsonMapper` manually in the constructor, applying the same `JsonMapperBuilderCustomizer` that `ObjectMapperConfiguration` provides:

```java
public UnitTest() {
    JsonMapper.Builder builder = new JsonMapper.Builder(new JsonFactoryBuilder().build());
    new ObjectMapperConfiguration().jsonMapperBuilderCustomizer().customize(builder);
    this.jsonMapper = builder.build();
}
```

This means if someone modifies the customizer, the unit tests will **fail** — just as they would in production. The manual instantiation is intentional; it keeps the test aligned with real configuration changes.

### 2. Spring beans in integration tests

`IntegrationTest` has `@SpringBootTest`, which starts the full application context. `JsonMapper` is injected via `@Autowired` from the bean — no manual construction needed. This is the correct approach for integration tests that depend on Spring wiring.

### 3. Test tags for pipeline selection

Test class hierarchy classes carry JUnit 5 `@Tag` annotations so the CI pipeline can run only unit or integration tests independently. Tag constants are defined in `BaseTest`:

- **`BaseTest.TAG_UNIT`** — run with `mvn test -Dgroups=UNIT` or `<groups>UNIT</groups>` in surefire
- **`BaseTest.TAG_INTEGRATION`** — run with `mvn test -Dgroups=INTEGRATION` or `<groups>INTEGRATION</groups>`

Concrete unit test classes reference `BaseTest.TAG_UNIT`; integration test classes reference `BaseTest.TAG_INTEGRATION`. This prevents integration tests from breaking the CI when Spring context startup fails.

## Test base class annotations

| Annotation | Purpose |
|---|---|
| `@Slf4j` | Lombok logger in all test classes |
| `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` | Respect `@Order` on test methods |
| `@ExtendWith(TimeExtension.class)` | Measure test execution time |
| `@ActiveProfiles({"test"})` | Use the `test` Spring profile |
| `@Tag(BaseTest.TAG_UNIT)` | **Only on `BaseTest`** — applies to unit test hierarchy |
| `@Tag(BaseTest.TAG_INTEGRATION)` | **Only on `BaseTest`** — applies to integration test hierarchy |
| `@SpringBootTest` | **Only on `IntegrationTest`** — starts full context |

## Tag constants

Defined in `BaseTest` as public static final fields:

```java
public static final String TAG_UNIT = "UNIT";
public static final String TAG_INTEGRATION = "INTEGRATION";
```

All test classes reference these constants via `BaseTest.TAG_UNIT` / `BaseTest.TAG_INTEGRATION` — never as raw strings. This ensures consistency and makes it easy to add new tag types in one place.

## JsonMapper configuration

Both `UnitTest` and `IntegrationTest` use the same two customizations:

- `WRITE_DATES_AS_TIMESTAMPS` disabled — dates serialize as ISO-8601 strings
- `FAIL_ON_UNKNOWN_PROPERTIES` enabled — fail-fast on unknown properties during deserialization

The `DateTimeFeature` and `DeserializationFeature` are imported from `tools.jackson.*`, the same Jackson implementation used throughout the project.
