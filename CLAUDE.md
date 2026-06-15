# AGENTS.md

Guidelines for AI agents working in this repository.

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
  credentials, or configuration values directly in Java code.

## Security Rules

- **Never commit secrets or credentials.** Do not include API keys, passwords, tokens, or certificates in the
  repository. Use environment variables or a secrets manager.
- **Do not log sensitive data.** Avoid printing personal information, tokens, or credentials in logs.

## Documentation Rules

- **Document public classes and methods with Javadoc.** All public APIs must have Javadoc explaining their purpose,
  parameters, and return values.
- **Keep the `README.md` up to date.** If a change affects how the project is configured, installed, or used, update the
  README. Do not leave it as a historical record.

## Documentation Maintenance Rules

- **Keep STRUCTURE.md in sync with the code layout.** Whenever you add, remove, or move a package, class, or
  configuration file, update STRUCTURE.md to reflect the change. Do not leave the structure map stale.
- **Keep deep-dive docs current.** If a change affects the architecture, CLI options, output format, or any detail
  described in `documentation/DOCLET.md`, `documentation/MCP.md`, `documentation/JACKSON.md`,
  `documentation/SECURITY.md`, `documentation/JDK-SOURCES.md`, or `documentation/TEST.md`, update those files too.
- **Do not update documentation "later."** If a change affects the docs, update them in the same session — before moving
  on. Stale documentation is worse than no documentation.

## MCP Tool Priority

- **Prefer MCP-provided tools over shell commands.** When an MCP server provides a tool for a task, use it first.
  If an MCP tool fails after 2 attempts, fall back to a shell command. Do not retry a failing MCP tool indefinitely.

## File Reading & Loop Prevention Rules

These rules prevent infinite loops, redundant work, and exploration traps when reading files or investigating code.

### File Reading

- **Read each file once.** Do not re-open, re-query, or re-search a file whose content you already got, unless it was
  modified after you read it. Cache and reuse what you already have in context.
- **Track files you have already read.** Keep a mental (or explicit) list of inspected files and their key findings and
  consult that list before issuing another read.
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
- **Always launch up to 3 Explore agents in parallel** (single message, multiple tool calls) rather than sequentially.
  If one agent can answer multiple questions, use it instead of splitting across sequential agents.
- **Set a read budget.** Limit yourself to a bounded number of file reads per task. If you exceed it without progress,
  stop and reassess your approach instead of reading more files.
- **Detect repetition.** If you find yourself reading the same file, directory, or symbol more than twice, treat it as a
  loop signal: stop, summarize what you know, and change strategy.
- **Stop after the goal is met.** As soon as you have the information needed to answer or to make a change, stop
  reading. Do not keep exploring "just in case".
- **Ask when stuck.** If the necessary information cannot be found within the read budget, ask for clarification rather
  than looping over the same files indefinitely.

### Tool Call Loops

- **Do not retry the same failing tool call.** If a Bash command fails, try at most 2 variations. If it still fails,
  stop and ask for help instead of retrying indefinitely.
- **Do not retry MCP tool calls in a loop.** If an MCP tool fails twice, fall back to a shell command or ask for
  clarification. Do not keep retrying the same MCP tool.
- **Set a tool call budget.** Limit yourself to a bounded number of tool calls per task. If you exceed it without
  progress, stop and reassess your approach.

## Project References

Deep-dive documentation lives in the `documentation/` directory. Consult these files before working in the areas they
cover — they prevent context loss and keep AGENTS.md from growing out of control.

- **[Project Structure](documentation/STRUCTURE.md)** — High-level layout, config hierarchy, build output
- **[Doclet](documentation/DOCLET.md)** — JSON doclet architecture, CLI options, output format, chunking
- **[MCP Server](documentation/MCP.md)** — MCP server setup and JetBrains adapter
- **[Jackson Config](documentation/JACKSON.md)** — Customizer pattern, YAML mapper convention
- **[Security Config](documentation/SECURITY.md)** — Actuator restrictions, logging paths
- **[JDK Sources](documentation/JDK-SOURCES.md)** — JDK source downloader, async execution, version parsing
- **[Test Architecture](documentation/TEST.md)** — Test class hierarchy, tags, JsonMapper setup

## Plan Files Rules

- **Only follow `pending` plans.** A plan is followed only when its YAML frontmatter is `status: pending`.
  Skip plans with `status: completed`, `status: failed`, or `status: deprecated` — they are historical records.
  Read the YAML frontmatter header of every plan file before following it.
- **Read the ACTIVE.md index first.** Always check `plans/ACTIVE.md` to see which plans are pending and which are
  completed/failed/deprecated. This is the authoritative list — never infer plan status from a plan file alone.
- **Save plans in the project `plans/` directory.** Any implementation plan, task breakdown, or design document must be
  stored under `<project-root>/plans/` (e.g., `plans/documentation-service-approach-a-fat-jar.md`). This is a real
  directory inside the project — NOT a hidden directory like `~/.claude/plans/`. Do NOT save plans in hidden
  directories; always use `plans/` at the project root. **Always verify the plan file exists at `plans/<name>.md` before moving on.**
- **Name plan files descriptively.** Use a clear, concise name that reflects the feature or fix being planned (e.g.,
  `auth-token-refresh.md`).
- **Keep plans up to date.** If implementation deviates from the plan, update it to reflect reality. Do not leave
  outdated plans as guides.
- **Keep plan status current.** New plans start as `pending`. When the work is fully implemented, change the status to
  `completed`; if the plan was attempted but did not succeed, set it to `failed`; if it is obsolete or superseded, set
  it to `deprecated`. Whenever a status changes, also move the plan to the matching section in `plans/ACTIVE.md`.
- **Use YAML frontmatter.** Every plan file must start with a YAML frontmatter block:

  ```yaml
  ---
  name: <descriptive-name>
  status: pending | completed | failed | deprecated
  date: YYYY-MM-DD
  ---
  ```

  `name` — a kebab-case identifier for the plan. `status` — one of `pending`, `completed`, `failed`, or `deprecated`.
  `date` — the date the plan was created.
