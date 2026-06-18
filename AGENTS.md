# Project Memory

Guidelines for AI agents working in this repository. This file is for **rules and conventions** — what the code should
do, how it should be structured, and what to avoid. How something works (architecture decisions, design choices,
step-by-step procedures) belongs in the `documentation/` directory, not here.

## Language Rules

- **Code is always written in English.** All identifiers (class names, method names, variable names, function names,
  constants, package names, etc.), as well as inline comments and commit messages, must be in English.
- **Documentation is always written in English.** All documentation, including Javadoc/KDoc, README files, design docs,
  and any other written documentation, must be in English.

## Code Style Rules

- **Do not use Fully Qualified Class Names in code.** Always use `import` statements and refer to classes by their
  simple name. For example, use `ObjectMapper` instead of `com.fasterxml.jackson.databind.ObjectMapper` in the code
  body. **Exception:** when two classes share the same simple name (e.g., `java.util.Date` and `java.sql.Date`), import
  the one that is used more frequently and use the Fully Qualified Class Name only for the less frequently used one.
- **Follow Java naming conventions.** Use `PascalCase` for classes, `camelCase` for methods and variables, and
  `UPPER_SNAKE_CASE` for constants.
- **Prefer constructor injection.** Use constructor-based dependency injection instead of `@Autowired` on fields. This
  facilitates testing and makes dependencies explicit.
- **Do not leave commented-out code.** If code is not used, remove it. Git history preserves it. Stop after one pass —
  do not re-check files for commented-out code after the initial cleanup.
- **Do not use `@SuppressWarnings` without justification.** If a warning needs to be suppressed, add a comment
  explaining why.
- **Prefer `Optional` over `null`.** Use `Optional` as a return type when a value may be absent, instead of returning
  `null`.

## Configuration / Spring Boot Rules

- **Do not hardcode configuration values.** Use `application.yaml` or environment variables. Do not put URLs, ports,
  credentials, or configuration values directly in Java code. **Exception:** API base URLs (e.g., `ADOPTIUM_BASE`)
  are acceptable constants — they are not secrets and do not change per deployment.
- **Use constructor injection.** Use constructor-based dependency injection for production code. `@Autowired` on fields
  is acceptable in test classes where a Spring context is not available.
- **Use Lombok `@Slf4j` for logging.** Do not use `System.out.println`, `java.util.logging`, or manual `Logger` fields.
- **Spring AI Transformer model URIs require a `file:` scheme.** Bare Windows paths like `C:\...\onnx\tokenizer.json`
  will not work — Spring AI tries to parse them as HTTP URLs. See [onnx/TRANSFORMER.md](onnx/TRANSFORMER.md) for
  details.

## Security Rules

- **Never commit secrets or credentials.** Do not include API keys, passwords, tokens, or certificates in the
  repository. Use environment variables or a secrets manager.
- **Do not log sensitive data.** Avoid printing personal information, tokens, or credentials in logs (via `@Slf4j`).

## Documentation Rules

- **Document public classes and methods with Javadoc.** All public APIs and methods called from outside the package must
  have Javadoc explaining their purpose, parameters, and return values.
- **Keep the `README.md` up to date.** If a change affects how the project is configured, installed, or used, update the
  README. Do not leave it as a historical record.

## Documentation Maintenance Rules

- **Keep `documentation/STRUCTURE.md` in sync with the code layout.** Whenever you add, remove, or move a package,
  class, or configuration file, update STRUCTURE.md to reflect the change. Do not leave the structure map stale.
- **Keep all files in `documentation/` current.** If a change affects the architecture, CLI options, output format,
  configuration, security setup, testing approach, or any other detail described in any file under `documentation/`,
  update that file in the same session — before moving on. Stale documentation is worse than no documentation.
- **Do not duplicate documentation.** If a detail is already well-documented in `documentation/`, do not repeat it
  in CLAUDE.md. Keep CLAUDE.md as a set of rules and conventions, not as a how-to guide.

## MCP Tool Priority

- **Prefer MCP-provided tools over shell commands.** When an MCP server provides a tool for a task, use it first.
  If an MCP tool or Bash command fails after 2 attempts, fall back to asking for clarification. Do not retry a
  failing tool indefinitely.

## File Reading & Loop Prevention Rules

These rules prevent infinite loops, redundant work, and exploration traps when reading files or investigating code.

### File Reading

- **Read each file once.** Do not re-open, re-query, or re-search a file whose content you already got, unless it was
  modified after you read it. Cache and reuse what you already have in context.
- **Re-read selectively after editing.** After an Edit or Write succeeds, the tool confirms the change was applied,
  but does not guarantee the result is correct. Re-read when:
  - The edit used `replaceAll: true` (could have modified more than intended).
  - The edit was a `Write` replacing the entire file (the new content might differ from what you expected).
  - You need to confirm the edit applied correctly and the file is in the expected state.
- **Do not re-read for paranoia.** If the edit was a simple, targeted replacement, trust the tool and move on.
  Re-reading just to "double-check" is a waste — the tool already confirmed it worked.
- **Loop signal: Read returns "Wasted call" or "file unchanged".** If a Read tool returns this message, stop reading
  and use the content you already have. This is not an error or a retry signal — it means you already have the data.
- **Prefer targeted reads.** Use structure queries, symbol search, or line-range reads to locate the relevant section
  instead of repeatedly scanning entire files.
- **Avoid circular navigation.** When following references between files (imports, includes, links), track visited paths
  and never revisit an already-visited file in the same traversal.
- **Bound directory traversal.** When walking directories, set a maximum depth, skip already-visited paths, and ignore
  generated or binary artifacts (e.g., `target`, build output, large binaries).
- **Respect symbolic links.** Do not follow symlinks that point back into an already-visited directory; this is a common
  source of infinite loops.

### Loop Prevention

- **Do not fall into exploration loops.** After two rounds of investigation (reading code, searching, verifying), stop
  and proceed with planning or implementation. Do not add a third or fourth round of "just one more check."
- **Detect repetition.** If you find yourself reading the same file, directory, or symbol more than twice, treat it as a
  loop signal: stop, summarize what you know, and change strategy.
- **Stop after the goal is met.** As soon as you have the information needed to answer or to make a change, stop
  reading. Do not keep exploring "just in case".
- **Ask when stuck.** If the necessary information cannot be found within the read budget, ask for clarification rather
  than looping over the same files indefinitely.

### Tool Call Loops

- **Do not retry failing tool calls in a loop.** If a tool fails after 2 attempts, fall back to a shell command or
  ask for clarification. Do not keep retrying the same tool.

## Testing Rules

- **Mark test classes with `@Tag`.** Use `BaseTest.TAG_UNIT` for unit tests and `BaseTest.TAG_INTEGRATION` for
  integration tests so the CI pipeline can run only the appropriate type. Never use raw strings — reference the
  constants. See [TEST.md](documentation/TEST.md) for the full test conventions.

## Project References

Deep-dive documentation lives in the `documentation/` directory; feature workspaces live in `features/`. Consult these
before working in the areas they cover — they prevent context loss and keep AGENTS.md from growing out of control.

- **[Project Structure](documentation/STRUCTURE.md)** — High-level layout, config hierarchy, build output, tech stack
- **[Feature Workspaces](features/FEATURES.md)** — Per-feature context bundles that inform implementation planning
- **[Doclet](documentation/DOCLET.md)** — JSON doclet architecture, CLI options, output format, chunking
- **[MCP Server](documentation/MCP.md)** — MCP server setup and JetBrains adapter
- **[Jackson Config](documentation/JACKSON.md)** — Customizer pattern, YAML mapper convention
- **[Security Config](documentation/SECURITY.md)** — Actuator restrictions, logging paths
- **[JDK Distribution](documentation/JDK-DISTRIBUTION.md)** — Adoptium distribution downloader, source selection,
  archive handling
- **[JDK Data](documentation/JDK-DATA.md)** — JDK source ZIP and JSON Javadoc data pipeline
- **[Database](documentation/DATABASE.md)** — JPA entities, Hibernate Search, kNN mapping, ingestion/search flows
- **[Test Architecture](documentation/TEST.md)** — Test class hierarchy, tags, JsonMapper setup
- **[Transformer Model](onnx/TRANSFORMER.md)** — ONNX embedding model, URI scheme requirements, model selection
- **[AI Models](documentation/AI-MODELS.md)** — Local AI models, quantization, and performance benchmarks
- **[Black Book](blackbook/BLACKBOOK.md)** — AI dev log: thoughts, decisions, gotchas
