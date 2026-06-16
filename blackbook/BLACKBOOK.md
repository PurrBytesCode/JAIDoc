# Black Book

> A log of thoughts, problems, and details encountered during AI-assisted development of this project.

## Purpose

This file captures the informal side of building JAIDoc with AI — things that don't belong in Javadoc, commit messages,
or documentation:

- **Thoughts** — Design decisions, trade-offs, and "why I chose this" moments that deserve a record
- **Problems** — Bugs, blockers, and gotchas that took time to resolve, so we don't forget how we fixed them
- **Details** — Small quirks, workarounds, and observations about the AI tools and workflow

Entries are always ordered with the most recent date at the top — the latest entry goes directly after the purpose
section. This ensures the current state of the project is always immediately visible. New notes on the same day are
appended under that date's heading, so entries from the same creative session stay grouped together visually.

It's not a formal artifact. It's a scratchpad for the things worth remembering, but that doesn't fit anywhere else.

## Rules

### Entry Format

- Each note is a separate HTML file named with the date: `YYYY-MM-DD.html`
- Markdown notes (`.md`) are also allowed for quick text-based entries
- Place notes in the `blackbook/` directory alongside this file
- The most recent entry goes directly after the Purpose section of this file

### Entry Content

- Use emoji prefixes to categorize entries:
  - `🔄` — Change, pivot, approach abandoned
  - `🏗️` — Architecture, structure, system design
  - `📋` — Planning, next steps, todo
  - `🐛` — Bugs, blockers, gotchas
  - `💡` — Ideas, insights, discoveries
  - `📝` — Documentation, notes, observations
- Keep entries concise and scannable
- Use bullet points for status updates
- Include links to related files or decisions when relevant

### File Naming

- Markdown: `YYYY-MM-DD.md`
- HTML: `YYYY-MM-DD.html`
- Both formats are welcome — use Markdown for quick text notes, HTML for richer formatting
