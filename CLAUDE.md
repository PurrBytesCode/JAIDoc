# Guiding coding agents

Guidelines for AI agents working in this repository. This file is for **rules and conventions** — what the code should
do, how it should be structured, and what to avoid. How something works (architecture decisions, design choices,
step-by-step procedures) belongs in the `documentation/` directory, not here.

These rules apply to every task in this project unless explicitly overridden.
Bias: caution over speed on non-trivial work. Use judgment on trivial tasks.

---

## Behavioral Rules

### Rule 1 — Think Before Coding

State assumptions explicitly. If uncertain, ask rather than guess.
Push back when a simpler approach exists. Stop when confused.

### Rule 2 — Simplicity First

Minimum code that solves the problem. Nothing speculative.
No features beyond what was asked. No abstractions for single-use code.

### Rule 3 — Surgical Changes

Touch only what you must. Clean up only your own mess.
Don't "improve" adjacent code, comments, or formatting. Match existing style.

### Rule 4 — Goal-Driven Execution

Define success criteria. Loop until verified.
Don't follow steps. Define success and iterate independently.

### Rule 5 — Token Budgets Are Not Advisory

Per-task and per-session token budgets are enforced. If approaching the limit, summarize and start fresh. Surface the
breach.

### Rule 6 — Read Before You Write

Before adding code, read exports, immediate callers, shared utilities.
If unsure why code is structured a certain way, ask.

### Rule 7 — Checkpoint After Every Significant Step

Summarize what was done, what's verified, what's left.
Don't continue from a state you can't describe back. Stop and restate.

### Rule 8 — Fail Loud

"Completed" is wrong if anything was skipped silently.
"Tests pass" is wrong if any fail.
Default to surfacing uncertainty, not hiding it.

### Rule 9 — Prefer MCP Tools Over Shell Commands

When an MCP server provides a tool for a task, use it first. If an MCP tool or shell command fails after 2 attempts,
fall back to asking for clarification. Do not retry a failing tool indefinitely.

---

## Language Rules

- **Code is always written in English.** All identifiers (class names, method names, variable names, function names,
  constants, package names, etc.), as well as inline comments and commit messages, must be in English.
- **Documentation is always written in English.** All documentation, including Javadoc/KDoc, README files, design docs,
  and any other written documentation, must be in English.

---

## Code Style Rules

- **Do not use Fully Qualified Class Names in code.** Always use `import` statements and refer to classes by their
  simple name. For example, use `ObjectMapper` instead of `com.fasterxml.jackson.databind.ObjectMapper` in the code
  body. **Exception:** when two classes share the same simple name (e.g., `java.util.Date` and `java.sql.Date`), import
  the one that is used more frequently and use the Fully Qualified Class Name only for the less frequently used one.
- **Follow Java naming conventions.** Use `PascalCase` for classes, `camelCase` for methods and variables, and
  `UPPER_SNAKE_CASE` for constants.
- **Do not leave commented-out code.** If code is not used, remove it. Git history preserves it. Stop after one pass —
  do not re-check files for commented-out code after the initial cleanup.
- **Do not use `@SuppressWarnings` without justification.** If a warning needs to be suppressed, add a comment
  explaining why.
- **Prefer `Optional` over `null`.** Use `Optional` as a return type when a value may be absent, instead of returning
  `null`.

---

## Configuration / Spring Boot Rules

- **Do not hardcode configuration values.** Use `application.yaml` or environment variables. Do not put URLs, ports,
  credentials, or configuration values directly in Java code. **Exception:** API base URLs (e.g., `ADOPTIUM_BASE`) are
  acceptable constants — they are not secrets and do not change per deployment.
- **Use constructor injection for production code.** `@Autowired` on fields is acceptable in test classes where a
  Spring context is not available.
- **Use Lombok `@Slf4j` for logging.** Do not use `System.out.println`, `java.util.logging`, or manual `Logger` fields.

---

## Security Rules

- **Never commit secrets or credentials.** Do not include API keys, passwords, tokens, or certificates in the
  repository. Use environment variables or a secrets manager.
- **Do not log sensitive data.** Avoid printing personal information, tokens, or credentials in logs (via `@Slf4j`).

---

## Documentation Rules

- **Javadoc must be in English.** All Javadoc comments must be written in English, matching the English-only policy for
  code and documentation.
- **Document public classes and methods with Javadoc.** All public APIs and methods called from outside the package must
  have Javadoc explaining their purpose, parameters, and return values.
- **Keep the `README.md` up to date.** If a change affects how the project is configured, installed, or used, update the
  README. Do not leave it as a historical record.

---

## Documentation Maintenance Rules

- **Keep `documentation/STRUCTURE.md` in sync with the code layout.** Whenever you add, remove, or move a package,
  class, or configuration file, update STRUCTURE.md to reflect the change. Do not leave the structure map stale.
- **Keep all files in `documentation/` current.** If a change affects the architecture, CLI options, output format,
  configuration, security setup, testing approach, or any other detail described in any file under `documentation/`,
  update that file in the same session — before moving on. Stale documentation is worse than no documentation.
- **Do not duplicate documentation.** If a detail is already well-documented in `documentation/`.

---

## File Reading & Loop Prevention Rules

- **Read each file once.** Do not re-open a file whose content you already have in context unless it was modified after
  you read it.
- **Stop exploring after two rounds.** After two rounds of investigation (reading, searching, verifying), stop and
  proceed with planning or implementation. Do not add a third round of "just one more check."
- **Stop as soon as the goal is met.** Once you have the information needed to answer or make a change, stop reading.
  Do not keep exploring "just in case."
- **Do not retry failing tool calls in a loop.** If a tool fails after 2 attempts, fall back to a shell command or ask
  for clarification (see also Rule 9).
- **Ask when stuck.** If the necessary information cannot be found within the read budget, ask for clarification rather
  than looping over the same files indefinitely.

---

## Testing Rules

- **Mark test classes with `@Tag`.** Use `BaseTest.TAG_UNIT` for unit tests and `BaseTest.TAG_INTEGRATION` for
  integration tests so the CI pipeline can run only the appropriate type. Never use raw strings — reference the
  constants. See [TEST.md](documentation/TEST.md) for the full test conventions.

---

## Project References

Deep-dive documentation lives in the `documentation/` directory; feature workspaces live in `features/`. Consult these
before working in the areas they cover.

### Architecture & Structure

- **[Project Structure](documentation/STRUCTURE.md)** — High-level layout, config hierarchy, build output, tech stack
- **[Feature Workspaces](features/FEATURES.md)** — Per-feature context bundles that inform implementation planning
- **[Jackson Config](documentation/JACKSON.md)** — Customizer pattern, YAML mapper convention
- **[Security Config](documentation/SECURITY.md)** — Actuator restrictions, logging paths

### Data & Distribution

- **[JDK Distribution](documentation/JDK-DISTRIBUTION.md)** — Adoptium distribution downloader, source selection,
  archive handling
- **[JDK Data](documentation/JDK-DATA.md)** — JDK source ZIP and JSON Javadoc data pipeline
- **[Database](documentation/DATABASE.md)** — JPA entities, Hibernate Search, kNN mapping, ingestion/search flows
- **[Doclet](documentation/DOCLET.md)** — JSON doclet architecture, CLI options, output format, chunking

### AI & Models

- **[Transformer Model](onnx/TRANSFORMER.md)** — ONNX embedding model, URI scheme requirements, model selection
- **[AI Models](documentation/AI-MODELS.md)** — Local AI models, quantization, and performance benchmarks

### Tooling & Tests

- **[MCP Server](documentation/MCP.md)** — MCP server setup and JetBrains adapter
- **[Test Architecture](documentation/TEST.md)** — Test class hierarchy, tags, JsonMapper setup

### History & Decisions

- **[Black Book](blackbook/BLACKBOOK.md)** — AI dev log: thoughts, decisions, gotchas
