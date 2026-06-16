---
allowed-tools: Glob, Read, Bash(git diff:*), Bash(git log:*), Bash(git status:*), Bash(git branch:*), Bash(git rev-parse:*), Bash(git merge-base:*)
argument-hint: [language: es, en | base branch: develop, main, release/x | ticket: ABC-123 — all optional, any order]
description: Generate a Pull Request title and description (Markdown) from the changes between the current branch and a target branch
---

## Context

- Current branch: !`git rev-parse --abbrev-ref HEAD`
- Available branches: !`git branch -a`
- Repo status: !`git status --short`
- Recent commits on this branch: !`git log --oneline -15`

## Task

Generate a **Pull Request title and description in Markdown** that summarizes the changes the current branch introduces
against a target (base) branch. This command **does not create the PR** — it only produces copy-paste-ready Markdown and
suggests the `gh pr create` command.

### Arguments

The provided arguments are: `$ARGUMENTS`

These may contain a **language**, a **base branch**, and/or a **ticket number**, in any order, and all are optional.
Auto-detect each one:

- **Language**: Spanish (`es`/`spanish`) or English (`en`/`english`). These are the only two supported languages. If the
  user explicitly passes any other language, accept it and honor it, but do not advertise or suggest other languages.
- **Base branch** (the branch the PR will merge *into*): a token that matches an existing branch in the "Available
  branches" list, or that looks like a branch path (contains `/`, e.g. `release/1.2`, or is one of
  `develop`/`main`/`master`). The default is **`develop`**. If `develop` does not exist in the branch list, fall back to
  the repository's main branch (`main`, otherwise `master`) and clearly note the fallback in your answer.
- **Ticket**: a token that is not a recognized language or branch (e.g. `ABC-123`, `1234`, `JIRA-42`, `#987`). There is
  **no fixed format** — tolerate whatever the user provides. If no ticket token is passed, try to auto-detect one from
  the **current branch name** (e.g. `feature/ABC-123-foo`) and from `Refs:`/`Closes:` footers in the branch commits. If
  none is found, omit the ticket footer.
- **Template trigger** (optional): the literal token `plantilla` or `template`. If present, also emit the reusable PR
  template (see "Reusable PR template"). Do not treat this token as a branch or ticket.

If no arguments were provided, default the language to **Spanish**, the base branch to **`develop`**, and auto-detect the
ticket as described above.

### Gathering the changes

A PR diff is **always** the current branch against the resolved base branch — never staged/unstaged changes. Once the
base branch is resolved, gather the changes by running, via the Bash tool:

1. `git log <base>..HEAD --oneline` — the commits unique to this branch (the PR's commit list).
2. `git diff <base>...HEAD --stat` — the per-file change summary (three-dot range = changes since the merge base, exactly
   what GitHub shows for a PR).
3. `git diff <base>...HEAD` — the full diff, to understand *what* actually changed.

Replace `<base>` with the resolved base branch. If `git log <base>..HEAD` is **empty**, the branch has no commits ahead
of the base — tell the user this and stop (there is nothing to open a PR for).

### Output language

Write the title, the description, and every section heading in the detected language. If **no** language was detected,
**use Spanish as the default**.

### PR Title

- A single concise line in imperative mood, following **Conventional Commits** style (`<type>(scope): <description>`),
  since this repository uses Conventional Commits. Limit it to **72 characters**.
- Choose the `type` that best represents the PR as a whole. If the branch mixes several types, pick the dominant one
  (typically `feat` or `refactor`) and let the description cover the rest.

### PR template (check first)

Before composing the description, check whether the repository defines a Pull Request template, using your file tools
(`Glob`/`Read`). Look in this order:

1. `.github/PULL_REQUEST_TEMPLATE.md` and `.github/pull_request_template.md`.
2. `.github/PULL_REQUEST_TEMPLATE/*.md` (a directory of multiple templates — if several exist, pick the most generic one
   and mention the alternatives).
3. A `templates/` folder at the repository root — any file whose name matches `*pull_request*`, `*pull-request*`, or
   `*pr*template*` (case-insensitive).

If a template **exists**, follow **its** structure and headings instead of the default sections below: fill each section
using the commits and diff, preserve any checklist or placeholders the template defines (leave checkboxes unchecked
unless the diff clearly satisfies them), and tell the user which template file you used. If **no** template exists, use
the default sections below.

### PR Description (Markdown)

Build the description with the following sections. The headings below are written in English; translate them to the
output language when needed (e.g. Resumen / Cambios / Cómo probar / Notas / Checklist when the output language is
Spanish, which is the default):

1. `## Summary` — a short paragraph explaining **what** the PR changes and **why**.
2. `## Changes` — a bullet list of the main changes, derived from the commit list and the diff. Group related changes;
   do not just paste the raw commit log.
3. `## How to test` — concrete steps to verify the changes locally (build/test commands such as `mvn`/`./mvnw`, and any
   manual steps). Keep it actionable.
4. `## Notes` — *only if relevant*: breaking changes, migrations, follow-ups, or caveats. Omit the section entirely when
   there is nothing to add.
5. `## Checklist` — a short review checklist with **unchecked** items (`- [ ]`), tailored to what the PR actually
   touches and based on the project conventions. Use these as a baseline:
   - `- [ ] The code follows the project conventions (English, constructor injection, no hardcoded values).`
   - `- [ ] Tests were added or updated with the correct `@Tag`.`
   - `- [ ] The documentation under `documentation/` and the `README` are up to date.`
   - `- [ ] The test suite passes locally.`
   - `- [ ] No secrets or credentials are included.`

   Drop items that clearly do not apply to the change and add change-specific ones when useful.

**Ticket footer:** if a ticket was detected, add a final line `Refs: <ticket>` at the very end of the description, keeping
the value exactly as provided/detected. If no ticket was found, do not add the footer.

**Do not hard-wrap paragraphs.** Write each paragraph on a single physical line (no manual line breaks inside a
paragraph). This lets the user copy the description without fixing line wraps. Bullet lists are fine as multiple lines.

### Output Format

**CRITICAL**: Return the title and description as **escaped Markdown** that will NOT be rendered by Claude Code. The user
needs to see and copy the raw Markdown source.

1. Output the **title** in its own escaped fenced code block.
2. Output the **description** in a second escaped fenced code block, so the user sees the raw Markdown (`##` headings,
   bullets, etc.) and can paste it directly into the GitHub PR form.
3. **Separate every section with a horizontal rule** — a plain `---` on its own line with a blank line above and below.
   Place it between the title block, the description block, and the suggested-command block. Do **not** escape it with a
   backslash and do not put it inside the code fences.
4. After the description, **suggest the `gh pr create` command** (it is only a suggestion — this command executes
   nothing). Because the body is multi-line Markdown, suggest saving the description to a file first and passing it with
   `--body-file`:
   - Resolve `--base` to the resolved base branch and `--head` to the current branch.
   - Use `--title` with the generated title.
   - Use `--body-file <file>` (e.g. `pr-description.md`) and tell the user to paste the description block into that file.
   Show the command in its own escaped fenced code block so it is copy-paste ready.

Example of correct output (base `develop`, language English, ticket `ABC-123`):

`````text
**PR title:**

```
feat(auth): add JWT refresh token rotation
```

---

**PR description (Markdown):**

```
## Summary

Implement automatic refresh token rotation to improve security. Old refresh tokens are invalidated after each use to prevent replay attacks.

## Changes

- Add refresh token rotation to the authentication flow.
- Invalidate the previous tokens after each refresh.
- Update the authentication middleware and the associated tests.

## How to test

1. `./mvnw test` to run the full suite.
2. Request a login, use the refresh token once, and verify the previous token is invalidated.

## Notes

BREAKING CHANGE: refresh tokens are now single-use.

## Checklist

- [ ] The code follows the project conventions (English, constructor injection, no hardcoded values).
- [ ] Tests were added or updated with the correct `@Tag`.
- [ ] The documentation under `documentation/` and the `README` are up to date.
- [ ] The test suite passes locally.
- [ ] No secrets or credentials are included.

Refs: ABC-123
```

---

**Suggested command** (save the description to `pr-description.md` first):

```
gh pr create --base develop --head feature/auth-refresh --title "feat(auth): add JWT refresh token rotation" --body-file pr-description.md
```
`````

### Reusable PR template (suggest, don't impose)

This command can also generate a reusable PR template for repositories that don't have one yet — but it never writes
files or imposes a template on its own.

- **By default**, if no template was found in the repository (see "PR template (check first)"), end your answer with a
  short one-line note (rendered as a Markdown comment/tip, in the output language) telling the user that a reusable
  template can be generated on demand. For example: `> 💡 This repository has no PR template. I can generate a reusable
  `.github/PULL_REQUEST_TEMPLATE.md` based on this structure — just ask.` Do **not** dump the full template unless asked.
- **Only when the user explicitly asks** for it (e.g. the arguments include a `plantilla`/`template` token, or they
  request it in a follow-up), output the canonical template below as an **escaped** Markdown block ready to save as
  `.github/PULL_REQUEST_TEMPLATE.md`. Translate the headings and comments to the output language. This is still only a
  suggestion — the user copies and saves it; the command does not write it.

Canonical template to emit on request (English shown; translate to the output language, e.g. Spanish, when needed):

`````text
```
## Summary

<!-- Explain what this PR changes and why. A short paragraph is enough. -->

## Changes

<!-- List the main changes (group related ones; do not paste the raw commit log). -->
-

## How to test

<!-- Concrete steps to verify the changes locally (build/tests and manual steps). -->
1.

## Notes

<!-- Optional: breaking changes, migrations, follow-ups, or caveats. Remove this section if it does not apply. -->

## Checklist

- [ ] The code follows the project conventions (English, constructor injection, no hardcoded values).
- [ ] Tests were added or updated with the correct `@Tag`.
- [ ] The documentation under `documentation/` and the `README` are up to date.
- [ ] The test suite passes locally.
- [ ] No secrets or credentials are included.

<!-- If it applies, reference the ticket at the end: Refs: ABC-123 -->
```
`````

### Notes

- Never invent changes that are not in the diff. If the diff is large, summarize at the level of modules/areas rather
  than listing every file.
- This command never runs `gh`, `git commit`, `git push`, or any state-changing command. It only reads git history and
  produces text.