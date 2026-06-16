---
name: blackbook-structured-directories
status: completed
date: 2026-06-15
---

# Restructure BLACKBOOK into a Directory with Markdown Rules + HTML Notes

## Context

The BLACKBOOK.md file has evolved — it now contains both rules for creating notes and the actual note entries. The user
wants to separate these concerns: BLACKBOOK.md should be the rules file, and individual notes should live as HTML files
in a `blackbook/` directory, with dates as filenames. This enables both Markdown and HTML versions of the notes.

## Plan

### 1. Create the blackbook/ directory

No files to move yet — just create the directory structure.

### 2. Transform BLACKBOOK.md into the rules file

- Remove the dated entries (2026-06-15 and 2026-06-14)
- Keep the Purpose section (with the ordering rules)
- Keep the Architecture section — update the folder diagram and references to show `blackbook/` instead of the root file
- Update internal references (mermaid diagrams, tables) to reflect the new structure

### 3. Create HTML note entries

Create `blackbook/2026-06-15.html` with the note content from today:

- JDK Source Approach Abandoned
- Current Status
- Next Steps

Use a simple, clean HTML template:

- Standard HTML5 boilerplate
- CSS for readability (clean typography, proper spacing)
- The date as the page title
- Each section as a `<section>` with appropriate headings

### 4. Update references

- **README.md** — Update the link from `BLACKBOOK.md` to `blackbook/BLACKBOOK.md`
- **CLAUDE.md** — Add a reference to `blackbook/BLACKBOOK.md` in the documentation rules section (keep it consistent
  with CLAUDE.md's own rule about keeping documentation current)

### 5. Update CLAUDE.md documentation rules

The CLAUDE.md rule says "Keep all files in documentation/ current" — this includes BLACKBOOK.md. Update the reference to
point to the new path.

## Files to Modify

| File                         | Action                                                |
|------------------------------|-------------------------------------------------------|
| `blackbook/`                 | Create directory                                      |
| `blackbook/BLACKBOOK.md`     | New — rules file (transformed from root BLACKBOOK.md) |
| `blackbook/2026-06-15.html`  | New — first HTML note entry                           |
| `BLACKBOOK.md`               | Delete — content moved to blackbook/                  |
| `README.md`                  | Update — change link to `blackbook/BLACKBOOK.md`      |
| `CLAUDE.md`                  | Update — reference new path                           |
| `documentation/STRUCTURE.md` | Update — folder architecture diagram                  |

## Verification

After completing the changes:

1. `blackbook/BLACKBOOK.md` exists and contains the rules (Purpose, ordering, structure)
2. `blackbook/2026-06-15.html` exists and contains the note entries from today
3. Root `BLACKBOOK.md` is deleted
4. `README.md` link points to `blackbook/BLACKBOOK.md`
5. `CLAUDE.md` references the new path
6. `documentation/STRUCTURE.md` folder architecture is updated
