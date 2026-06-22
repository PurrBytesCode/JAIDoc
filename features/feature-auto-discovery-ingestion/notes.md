# Notes

## Observations

- The service is very simple — just a few methods — but it's the entry point for automatic ingestion, which is how most
  users will get their documentation indexed.
- The version extraction from filename is naive: `fileName.substring(0, fileName.length() - 4)` — this assumes the
  filename is exactly `<version>.zip` with no other extensions.

## Decisions

- Use `@ConditionalOnProperty` to conditionally enable/disable the service — this is a Spring-native way to control
  feature activation without modifying the code
- Process each version independently with try-catch — this ensures one version's failure doesn't block others

## Open Questions

- Should we add a manifest check before ingestion (like `isVersionGenerated()` does) to skip ZIPs that don't contain
  `index.json`?
