---
allowed-tools: Read, Write, Edit, Glob, Bash(date:*)
argument-hint: [category: 🔄|🏗️|📋|🐛|💡|📝 or change|arch|plan|bug|idea|docs] <note text> — category optional
description: Append or create a dated Black Book note in both Markdown and HTML, following blackbook/BLACKBOOK.md
---

## Context

- Black Book rules and rendering spec: `blackbook/BLACKBOOK.md`
- Existing Black Book files: !`ls blackbook`
- Today's date: !`date +%Y-%m-%d`

## Task

Write a new Black Book note for **today** and persist it to disk in **both** formats — the Markdown file
(`blackbook/YYYY-MM-DD.md`) and the HTML file (`blackbook/YYYY-MM-DD.html`) — strictly following the rules and the
rendering spec in `blackbook/BLACKBOOK.md`. Unlike the other `jaidoc` commands, this command **does** write files.

### Arguments

The provided arguments are: `$ARGUMENTS`

`$ARGUMENTS` is the note to record. It may **optionally** begin with a category token — either an emoji
(`🔄`, `🏗️`, `📋`, `🐛`, `💡`, `📝`) or a name (`change`, `arch`, `plan`, `bug`, `idea`, `docs`). The rest is the note
content (a short topic, or a fuller description with details).

- If a category token is present, use it.
- If it is absent, infer the most fitting category from the content using the emoji taxonomy in `BLACKBOOK.md`
  ("Entry Content" → emoji prefixes).
- If `$ARGUMENTS` is empty, ask the user what they want to log instead of inventing an entry.

### Step 0 — Read the spec first

Read `blackbook/BLACKBOOK.md` in full before doing anything else. It is the source of truth for: the emoji →
category → CSS-class → tab-label mapping, the HTML page skeleton, the Markdown → HTML element mapping, the Mermaid
rendering rules (escaping + the head `<style>` and body `<script>` blocks), the same-day appending rule, and the
cross-entry navigation/index rules. **Do not duplicate or guess these — follow what the file says.** If
`BLACKBOOK.md` and these instructions ever disagree, `BLACKBOOK.md` wins.

### Step 1 — Resolve the date and target files

1. Determine today's date as `YYYY-MM-DD` (from the "Today's date" context line, or your environment date).
2. Using `Glob`/`Read`, check whether `blackbook/YYYY-MM-DD.md` and `blackbook/YYYY-MM-DD.html` already exist.
   - **Both exist** → this is a **same-day append** (Step 3).
   - **Neither exists** → this is a **new day** (Step 4).
   - If only one of the two exists, treat it as a sync repair: create the missing file and append to the present one
     so both end up consistent.

### Step 2 — Compose the entry

- Write the note in **English** (project convention — the Black Book is documentation).
- Produce a concise, scannable entry per `BLACKBOOK.md` "Entry Content": a short title, bullet points for status
  updates, links to related files/decisions when relevant.
- Pick the structure that fits the content: plain paragraphs for a quick thought; bullets for status; tables, fenced
  code blocks, blockquotes (callouts), or a Mermaid diagram for richer entries. Only add a Mermaid diagram when it
  genuinely helps — most notes will not need one.
- Keep the title free of the emoji (the emoji lives in the category tab/heading prefix, not the `<h2>`).

### Step 3 — Same-day append (files already exist)

Read both files, then append the new entry to each, keeping them in sync:

**Markdown (`blackbook/YYYY-MM-DD.md`):**

- Append a new `### EMOJI Entry Title` block after the last existing entry (end of file), with one blank line of
  separation. Use `####` for any subsections. Mermaid goes in a fenced ` ```mermaid ` block.

**HTML (`blackbook/YYYY-MM-DD.html`):**

1. Insert the new `<section class="entry cat-X" id="slug">…</section>` immediately **before** the
   `<footer class="bb-foot">`, following the Page Structure skeleton (tab label, `<h2>`, body).
2. Add a matching `<li class="cat-X"><a href="#slug">Title</a></li>` at the **end** of the
   `<ul class="bb-toc">` list (nest a `toc-sub` list if the entry has subsections).
3. Render the body using the Markdown → HTML mapping; HTML-escape all `<pre>`/`<code>` content.
4. **Mermaid:** if the entry adds a diagram and the page did **not** already have one, also inject the head
   `<style>` block and the body `<script>` block from `BLACKBOOK.md`. If the page already had a diagram, leave those
   blocks as-is.

The Index in `BLACKBOOK.md` already lists today — leave it unchanged on a same-day appended.

### Step 4 — New day (files do not exist yet)

1. **Create `blackbook/YYYY-MM-DD.md`** starting with `# YYYY-MM-DD`, then the new `### EMOJI Entry Title` entry.
2. **Create `blackbook/YYYY-MM-DD.html`** from the Page Structure skeleton: set the `<title>`, `.bb-date`, the single
   TOC `<li>`, the entry `<section>`, and a footer. As the newest entry, its footer is `[ ← <previous-date> | Index ]`
   (find `<previous-date>` as the latest existing dated file). If there is no previous file, use just `[ Index ]`.
   Include the Mermaid head/script blocks only if this entry has a diagram.
3. **Update the previously newest file's footer** to add the `<new-date> →` next link (so it becomes
   `[ ← prev | Index | next → ]`, or `[ Index | next → ]` if it had no previous).
4. **Update `blackbook/BLACKBOOK.md`** — prepend a new line to the `## Index` section (most recent first):
   `- **YYYY-MM-DD** — [HTML](YYYY-MM-DD.html) · [Markdown](YYYY-MM-DD.md)`.

### Rules & guardrails

- Keep `.md` and `.html` byte-for-byte equivalent in **content** (same entries, titles, categories, body) — only the
  formatting differs.
- Never hardcode anything that contradicts `BLACKBOOK.md`; if the spec changes, this command's output follows it.
- Read each file once, make targeted edits, and stop — do not re-scan files after editing them.
- Do not touch entries from other days and do not reorder existing entries.

### Output

After writing, briefly report (in the user's language):

1. Whether you **appended** to an existing day or **created** a new day.
2. The exact files are written or edited (paths).
3. The entry's category and title, and whether a Mermaid diagram was included.