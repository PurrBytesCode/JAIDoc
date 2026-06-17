# Features

Feature workspaces for JAIDoc. A feature workspace is a **context bundle**: it gathers all the
documentation an AI needs to produce an implementation plan — purpose, scope, data flow, mock data,
and notes. The implementation **plan itself is independent and is not stored here**; a feature only
provides the grounded context that makes a good plan possible.

## Index

| Feature             | Status   | Description                |
|---------------------|----------|----------------------------|
| [Example](example/) | Template | Template feature workspace |

## Feature Layout

Each feature lives in its own kebab-case directory under `features/`:

| File                  | Role                                                               | Required |
|-----------------------|--------------------------------------------------------------------|----------|
| `README.md`           | Entry point — feature spec (context, scope, implementation notes)  | Yes      |
| `data-flow.md`        | End-to-end data flow: sequence, data models, error states          | Yes      |
| `mock-requests.json`  | Mock request/response pairs aligned with the API contract          | Yes      |
| `notes.md`            | Observations, decisions, and open questions about the workspace    | No       |
| `scripts/validate.sh` | Checks the workspace has the required files and frontmatter         | No       |

`README.md` carries YAML frontmatter (`name`, `status`, `date`) so the feature's status is trackable.

## Status

Status describes the **feature**, not a plan. A feature is only ever a template, not yet built, or built — there is no
"in progress" state to keep updating.

| Status            | Meaning                                  | AI Action                                              |
|-------------------|------------------------------------------|--------------------------------------------------------|
| `template`        | A reference example — not a real feature | **Never implement** — use as a structural guide only   |
| `not implemented` | Documented but not yet built             | Read as context to design and build the implementation |
| `implemented`     | Complete and in the codebase             | Reference for context; don't duplicate existing work   |

**Templates are not real features.** The `example/` feature describes the structure a real feature should have — not
a feature to implement. When creating a new feature, copy the template, rename it, and fill in the real content.

## Naming Conventions

- Feature directories use kebab-case: `feature-slug`
- Prefix with `feature-` for new features, `fix-` for bug fixes
- Multi-word names use kebab-case: `jdk-docs-ingestion`, `auth-token-refresh`
