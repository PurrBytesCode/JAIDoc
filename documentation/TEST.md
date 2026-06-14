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
├── @Disabled("${test.integration.enabled:false}")
├── @Autowired protected JsonMapper jsonMapper
└── Purpose: integration tests — need Spring context and bean wiring
```

## Test base class annotations

| Annotation                                              | Purpose                                                        |
|---------------------------------------------------------|----------------------------------------------------------------|
| `@Slf4j`                                                | Lombok logger in all test classes                              |
| `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` | Respect `@Order` on test methods                               |
| `@ExtendWith(TimeExtension.class)`                      | Measure test execution time — logs after each test method      |
| `@ActiveProfiles({"test"})`                             | Use the `test` Spring profile                                  |
| `@Tag(BaseTest.TAG_UNIT)`                               | **Only on `BaseTest`** — applies to unit test hierarchy        |
| `@Tag(BaseTest.TAG_INTEGRATION)`                        | **Only on `BaseTest`** — applies to integration test hierarchy |
| `@SpringBootTest`                                       | **Only on `IntegrationTest`** — starts full context            |
| `@Disabled("${test.integration.enabled:false}")`        | **Only on `IntegrationTest`** — skips unless enabled           |

## Test class annotation rules

- **`BaseTest`** carries both `@Tag` annotations (UNIT and INTEGRATION). This means `UnitTest` inherits UNIT and
  `IntegrationTest` inherits INTEGRATION automatically.
- **`IntegrationTest`** adds `@SpringBootTest` and `@Disabled` — it never runs unless `test.integration.enabled=true`.
- **`UnitTest`** does NOT have `@SpringBootTest` — it builds `JsonMapper` manually. No Spring context.
- **`@Order`** on test methods respects the `@TestMethodOrder` set on `BaseTest`. Test methods can be ordered with
  `@Order(1)`, `@Order(2)`, etc.
- **`@TestInstance(PER_CLASS)`** is NOT used — all tests use the default `PER_METHOD` test instance strategy.
- **`@Nested`** is used for organizing related test methods into logical groups within a single test class (e.g.,
  `@Nested class NormalizeVersionTest`).
- **`@TempDir`** is used in integration tests to get a temporary directory provided by JUnit 5 (e.g.,
  `@TempDir Path tempDir;`).

## Design principles

## No Spring in unit tests

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

## Tag constants

Defined in `BaseTest` as public static final fields:

```java
public static final String TAG_UNIT = "UNIT";
public static final String TAG_INTEGRATION = "INTEGRATION";
```

All test classes reference these constants via `BaseTest.TAG_UNIT` / `BaseTest.TAG_INTEGRATION` — never as raw strings.
This ensures consistency and makes it easy to add new tag types in one place.

## TimeExtension

A JUnit 5 extension that measures and logs test execution time. It implements `BeforeTestExecutionCallback` and
`AfterTestExecutionCallback`, logging the method name and execution time in milliseconds after each test:

```
Method: jdk17_returnsJdk17uGaTag took 12 MS
```

This is useful for detecting regressions in test performance. The extension is applied to `BaseTest` via
`@ExtendWith(TimeExtension.class)` so **all** test classes inherit it automatically.

## Test directory structure

Tests mirror the main source tree structure:

```
src/test/java/com/purrbyte/ai/
├── test/                          # Test base classes and utilities
│   ├── BaseTest.java
│   ├── UnitTest.java
│   ├── IntegrationTest.java
│   └── extension/
│       └── TimeExtension.java
├── util/
│   ├── JdkSourceDownloaderTest.java
│   └── JdkSourceDownloaderIntegrationTest.java
├── doclet/
│   ├── DocTreeJsonTest.java
│   └── ChunkWriterTest.java
├── JAIDocTest.java                # Integration test for main class
```

Test classes use the same package structure as their production counterparts. Test-specific classes (base classes,
extensions) go under `test/` to keep them separate from production classes.

## Assertion library

Tests use **AssertJ** for assertions. All test classes have access to AssertJ's fluent API:

- `assertThat(actual).isEqualTo(expected)` — value comparison
- `assertThatThrownBy(() -> ...).isInstanceOf(Exc.class)` — exception checking
- `assertThat(list).hasSize(n)` — collection assertions
- `assertThat(path).exists()` — file system assertions
- `assertThatThrownBy(() -> ...).hasMessageContaining("substring")` — message checking

No need to import JUnit's `org.junit.Assert` or any other assertion library — AssertJ is the standard for this project.

## Test method patterns

### `@Order` — ordered test methods

Test methods can be ordered with `@Order(n)` where `n` is an integer. The `@TestMethodOrder` on `BaseTest` ensures
order is respected. This is used when test methods have dependencies (e.g., a setup test must run before a verification
test):

```java

@Test
@Order(1)
void createsOutputDirectory() { ...}

@Test
@Order(2)
void outputDirectoryExists() { ...}
```

### `@Nested` — grouped test methods

Related test methods are grouped into `@Nested` inner classes for readability:

```java
class JdkSourceDownloaderTest extends UnitTest {
    @Nested
    class GetTagNameForVersionTest {
        @Test
        void jdk17_returnsJdk17uGaTag() { ...}

        @Test
        void jdk21_returnsJdk21uGaTag() { ...}
    }
}
```

### `@TempDir` — temporary directories

Integration tests use JUnit 5's `@TempDir` to get a temporary directory that is automatically cleaned up:

```java

@SpringBootTest
@TempDir
Path tempDir;
```

The directory is unique per test method and is deleted after the test completes. This is used for testing file I/O
operations without polluting the filesystem.

### `@Order` on integration tests

Integration tests that involve multiple steps (e.g., download → extract → run) use `@Order` to ensure execution
sequence. The `@Order` values are typically sequential integers (1, 2, 3...).

## Integration test patterns

### Actual network calls

Some integration tests make real network calls (e.g., `JdkSourceDownloaderIntegrationTest` downloads JDK source ZIPs
from GitHub). These tests require network access and may be slow. They are disabled by default via `@Disabled`.

### File system operations

Tests that verify file operations use `@TempDir` to avoid side effects. After the test, the temporary directory is
cleaned up automatically.

### ChunkWriterTest — real file writes

`ChunkWriterTest` writes actual JSONL files to a `@TempDir` directory and verifies the output content. It uses the
`jsonMapper` injected from the Spring context to serialize objects.

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
