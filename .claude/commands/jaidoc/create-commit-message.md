---
allowed-tools: Bash(git diff:*), Bash(git status:*), Bash(git log:*)
argument-hint: [language: es, en and/or ticket: ABC-123 — both optional, any order]
description: Generate a Conventional Commits message based on current git changes
---

## Context

- Current repo status: !`git status --short`
- Staged changes: !`git diff --cached`
- Unstaged changes: !`git diff`
- Last 5 commits (for style consistency): !`git log --oneline -5`

## Task

Generate a commit message strictly following the **Conventional Commits**
specification (https://www.conventionalcommits.org/).

### Arguments

The provided arguments are: `$ARGUMENTS`

These may contain a **language** and/or a **ticket number**, in any order, and both are optional. Auto-detect each one:

- **Language**: Spanish (`es`/`spanish`) or English (`en`/`english`). These are the only two supported languages. If
  the user explicitly passes any other language, accept it and honor it, but do not advertise or suggest other
  languages.
- **Ticket**: anything that is not a recognized language token (e.g. `ABC-123`, `1234`, `JIRA-42`, `#987`). There is
  **no fixed format** — tolerate whatever the user provides. If no ticket-like token is present, simply omit the ticket
  footer and work normally.

If no arguments were provided, default the language to **Spanish** and produce the message with no ticket footer.

### Language

- Write the commit description in the detected language.
- If **no** language was detected, **use Spanish as the default** to generate the message.

### Ticket

- If a ticket was detected, add it as a footer line at the very end of the commit body using the form `Refs: <ticket>`.
- Keep the ticket value exactly as the user typed it (do not reformat it).
- If no ticket was provided, do **not** add the footer at all.

### Conventional Commits Rules

The format is:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Allowed types:**

- `feat`: a new feature
- `fix`: a bug fix
- `docs`: documentation-only changes
- `style`: formatting changes (no logic affected)
- `refactor`: code restructuring without changing functionality
- `perf`: performance improvement
- `test`: adding or fixing tests
- `build`: changes to the build system or dependencies
- `ci`: CI configuration changes
- `chore`: maintenance tasks

### Instructions

1. Analyze the diff (prefer staged changes; fall back to unstaged if nothing is staged).
2. Determine the most appropriate `type` based on the changes.
3. Infer a `scope` if the changes are concentrated in a specific module or area.
4. Write a concise description in imperative mood (**only the subject line** is limited to 72 characters).
5. If the changes are complex, add an explanatory body.
6. **Do not hard-wrap the body.** Write each body paragraph on a **single physical line** (no manual line breaks inside
   a paragraph). Separate paragraphs with one blank line. This lets the user copy the message without fixing line wraps.
7. If a ticket was detected, append the `Refs: <ticket>` footer as the last line of the body.
8. If there are breaking changes, include them as a footer with `BREAKING CHANGE:`.
9. After the message, suggest the git commands needed to create the commit: a `git add` for the relevant files and the
   matching `git commit` command that reproduces the generated message.

### Output Format

**CRITICAL**: Return the commit message as **escaped markdown** that will NOT be rendered by Claude Code. The user needs
to see and copy the raw markdown source.

1. Output the commit message wrapped in a fenced code block, but escape the backticks so the user sees the raw markdown
   including the fence markers.
2. The user must be able to copy the entire output — including the triple backticks — as a ready-to-paste markdown
   snippet.
3. Keep every body paragraph on one line (see instruction 6) so the copied text needs no reformatting.
4. **Separate every section with a horizontal rule** — a plain `---` on its own line with a blank line above and below.
   Place it between the commit message block and the git commands block, and between consecutive commits when splitting.
   Do **not** escape it with a backslash and do not put it inside the code fences.
5. After the message block, **suggest the git commands** needed to create the commit:
   - A `git add` command listing the relevant files (use `git add .` only if every change belongs to the commit).
   - The matching `git commit` command that reproduces the message. Use one `-m` per paragraph
     (subject, body, footer) so the structure is preserved, or a single `-m` when there is no body.
     Show these commands in their own escaped fenced code block so they are copy-paste ready.
     This command does **not** execute anything; it only suggests the commands.

Example of correct output (with a ticket `ABC-123`):

`````text
```
feat(auth): add JWT refresh token rotation

Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use to prevent replay attacks.

BREAKING CHANGE: refresh tokens are now single-use

Refs: ABC-123
```

---

Suggested git commands:

```
git add src/auth/jwt.ts src/auth/refresh.ts
git commit -m "feat(auth): add JWT refresh token rotation" -m "Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use to prevent replay attacks." -m "BREAKING CHANGE: refresh tokens are now single-use" -m "Refs: ABC-123"
```
`````

### Splitting Into Multiple Commits

If multiple changes could justify separate commits, suggest splitting them and provide a message for each one, each in
its own escaped markdown block. For each suggested commit:

1. Add a short plain-text note before the block explaining the suggested split.
2. List the specific files that belong to that commit using a bullet list with the label **Files:** (or the
   equivalent in the chosen language). Group files by their relationship to the commit's purpose.
3. Then show the commit message in its escaped markdown block.
4. After each commit message block, suggest the `git add` and `git commit` commands for that specific
   commit (staging only the files listed for it), in their own escaped code block.
5. Separate each commit's block from the next with a `---` horizontal rule.
6. If a ticket was detected, repeat the same `Refs: <ticket>` footer on every commit.

Example of correct multi-commit output (with a ticket `ABC-123`):

`````text
The changes touch authentication logic and documentation separately. I suggest splitting into two commits:

**Commit 1** — New auth feature

**Files:**
- src/auth/jwt.ts
- src/auth/refresh.ts
- src/middleware/auth.middleware.ts

```
feat(auth): add JWT refresh token rotation

Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use.

BREAKING CHANGE: refresh tokens are now single-use

Refs: ABC-123
```

Suggested git commands:

```
git add src/auth/jwt.ts src/auth/refresh.ts src/middleware/auth.middleware.ts
git commit -m "feat(auth): add JWT refresh token rotation" -m "Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use." -m "BREAKING CHANGE: refresh tokens are now single-use" -m "Refs: ABC-123"
```

---

**Commit 2** — Documentation update

**Files:**
- docs/auth.md
- README.md

```
docs(auth): update authentication flow documentation

Add refresh token rotation details and migration guide for the new single-use token policy.

Refs: ABC-123
```

Suggested git commands:

```
git add docs/auth.md README.md
git commit -m "docs(auth): update authentication flow documentation" -m "Add refresh token rotation details and migration guide for the new single-use token policy." -m "Refs: ABC-123"
```
`````
