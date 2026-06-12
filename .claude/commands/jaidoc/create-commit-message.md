---
allowed-tools: Bash(git diff:*), Bash(git status:*), Bash(git log:*)
argument-hint: [ language: es, en, pt, or full name ]
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

### Language

- If the user provided a language as argument (`$ARGUMENTS`), write the commit description in that language.
- Accept both full names and short codes: `es` or `spanish` = Spanish, `en` or `english` = English, and so on for any
  ISO 639-1 code.
- If **no** argument was provided, **use spanish as defailt to generate the message** .

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
4. Write a concise description in imperative mood (max 72 characters in the subject line).
5. If the changes are complex, add an explanatory body.
6. If there are breaking changes, include them as a footer with `BREAKING CHANGE:`.
7. After the message, suggest the git commands needed to create the commit: a `git add` for the
   relevant files and the matching `git commit` command that reproduces the generated message.

### Output Format

**CRITICAL**: Return the commit message as **escaped markdown** that will NOT be rendered by Claude Code. The user needs
to see and copy the raw markdown source.

1. Output the commit message wrapped in a fenced code block, but escape the backticks so the user sees the raw markdown
   including the fence markers.
2. The user must be able to copy the entire output — including the triple backticks — as a ready-to-paste markdown
   snippet.
3. After the message block, **suggest the git commands** needed to create the commit:
    - A `git add` command listing the relevant files (use `git add .` only if every change belongs to the commit).
    - The matching `git commit` command that reproduces the message. Use one `-m` per paragraph
      (subject, body, footer) so the structure is preserved, or a single `-m` when there is no body.
      Show these commands in their own escaped fenced code block so they are copy-paste ready.
      This command does **not** execute anything; it only suggests the commands.

Example of correct output:

`````text
```
feat(auth): add JWT refresh token rotation

Implement automatic refresh token rotation to improve security.
Old refresh tokens are invalidated after each use to prevent
replay attacks.

BREAKING CHANGE: refresh tokens are now single-use
```

Suggested git commands:

```
git add src/auth/jwt.ts src/auth/refresh.ts
git commit -m "feat(auth): add JWT refresh token rotation" -m "Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use to prevent replay attacks." -m "BREAKING CHANGE: refresh tokens are now single-use"
```
`````

### Splitting Into Multiple Commits

If multiple changes could justify separate commits, suggest splitting them and provide a message for each one, each in
its own escaped markdown block. For each suggested commit:

1. Add a short plain-text note before the block explaining the suggested split.
2. List the specific files that belong to that commit using a bullet list with the label **Archivos:** (or the
   equivalent in the chosen language). Group files by their relationship to the commit's purpose.
3. Then show the commit message in its escaped markdown block.
4. After each commit message block, suggest the `git add` and `git commit` commands for that specific
   commit (staging only the files listed for it), in their own escaped code block.

Example of correct multi-commit output:

`````text
The changes touch authentication logic and documentation separately. I suggest splitting into two commits:

**Commit 1** — New auth feature

**Files:**
- src/auth/jwt.ts
- src/auth/refresh.ts
- src/middleware/auth.middleware.ts

```
feat(auth): add JWT refresh token rotation

Implement automatic refresh token rotation to improve security.
Old refresh tokens are invalidated after each use.

BREAKING CHANGE: refresh tokens are now single-use
```

Suggested git commands:

```
git add src/auth/jwt.ts src/auth/refresh.ts src/middleware/auth.middleware.ts
git commit -m "feat(auth): add JWT refresh token rotation" -m "Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use." -m "BREAKING CHANGE: refresh tokens are now single-use"
```

**Commit 2** — Documentation update

**Files:**
- docs/auth.md
- README.md

```
docs(auth): update authentication flow documentation

Add refresh token rotation details and migration guide
for the new single-use token policy.
```

Suggested git commands:

```
git add docs/auth.md README.md
git commit -m "docs(auth): update authentication flow documentation" -m "Add refresh token rotation details and migration guide for the new single-use token policy."
```
`````