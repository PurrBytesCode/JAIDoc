# Notes

## Observations

- This is a template — replace all `<placeholders>` with real content
- Keep `mock-requests.json` aligned with the actual API contract
- Update `data-flow.md` when the architecture changes

## Decisions

- Use kebab-case for file names and directory names
- YAML frontmatter is required for `plan.md` to support lifecycle tracking
- Data flow diagrams should be in `data-flow.md` — not as images, to keep them editable

## Open Questions

- Should mock-requests.json be generated from the OpenAPI spec?
- How do we handle version mismatches between request and response?
- Should we add a `scripts/verify.sh` that validates plan.md against the schema?
