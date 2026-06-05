# AGENTS.md

Guidelines for AI agents working in this repository.

## Language Rules

- **Code is always written in English.** All identifiers (class names, method names, variable names, function names,
  constants, package names, etc.), as well as inline comments and commit messages, must be in English.
- **Documentation is always written in English.** All documentation, including Javadoc/KDoc, README files, design docs,
  and any other written documentation, must be in English.

## File Reading Rules

These rules prevent infinite loops and redundant work when reading files.

- **Read each file once.** Do not re-open, re-query, or re-search a file whose content you already obtained, unless it
  was modified after you read it. Cache and reuse what you already have in context.
- **Track files you have already read.** Keep a mental (or explicit) list of inspected files and their key findings, and
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
- **Ask when stuck.** If the needed information cannot be found within the read budget, ask for clarification rather
  than looping over the same files indefinitely.

