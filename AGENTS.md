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
- **Do not leave commented-out code.** If code is not used, remove it. Git history preserves it.
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
  README.

## File Reading Rules

These rules prevent infinite loops and redundant work when reading files.

- **Read each file once.** Do not re-open, re-query, or re-search a file whose content you already got, unless it was
  modified after you read it. Cache and reuse what you already have in context.
- **Track files you have already read.** Keep a mental (or explicit) list of inspected files and their key findings and
  consult that list before issuing another read.
- **Stop after the goal is met.** As soon as you have the information needed to answer or to make a change, stop
  reading. Do not keep exploring "just in case".
- **Set a read budget.** Limit yourself to a bounded number of file reads per task. If you exceed it without progress,
  stop and reassess your approach instead of reading more files.
- **Detect repetition.** If you find yourself reading the same file, directory, or symbol more than twice, treat it as a
  loop signal: stop, summarize what you know, and change strategy.
- **Prefer targeted reads.** Use structure queries, symbol search, or line-range reads to locate the relevant section
  instead of repeatedly scanning entire files.
- **Avoid circular navigation.** When following references between files (imports, includes, links), track visited paths
  and never revisit an already-visited file in the same traversal.
- **Bound directory traversal.** When walking directories, set a maximum depth, skip already-visited paths, and ignore
  generated or binary artifacts (e.g., `target`, build output, large binaries).
- **Respect symbolic links.** Do not follow symlinks that point back into an already-visited directory; this is a common
  source of infinite loops.
- **Ask when stuck.** If the necessary information cannot be found within the read budget, ask for clarification rather
  than looping over the same files indefinitely.
