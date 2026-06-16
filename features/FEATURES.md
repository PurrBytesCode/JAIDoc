# Features

Feature workspaces for JAIDoc. Each feature has its own folder with a markdown file describing the purpose, current
status, scope, implementation details, and cross-references.

## Index

| Feature             | Status   | Description                |
|---------------------|----------|----------------------------|
| [Example](example/) | Template | Template feature workspace |

## Status

| Status        | Meaning                                    | AI Action                                 |
|---------------|--------------------------------------------|-------------------------------------------|
| `template`    | A reference example — not a real feature   | **Never implement** — use as a guide only |
| `planned`     | Feature is planned but not yet implemented | Read to understand intent, then implement |
| `in progress` | Feature is actively being worked on        | Follow the plan, update status when done  |
| `implemented` | Feature is complete and tested             | Reference in plans, don't duplicate work  |

**Templates are not real features.** The `example/` feature describes the structure a real feature should have — not
a feature to implement. When creating a new feature, copy the template, rename it, and fill in the real content.

## Naming Conventions

- Feature directories use kebab-case: `feature-slug`
- Prefix with `feature-` for new features, `fix-` for bug fixes
- Multi-word names use kebab-case: `jdk-docs-ingestion`, `auth-token-refresh`
