# Notes

## Observations

- The method is extremely simple — just 10 lines of code — but handles many edge cases (exact match, version-prefixed, Windows paths, deep nesting)
- The `replace('\\', '/')` call is necessary because ZIP files created on Windows use backslashes as path separators, while those created on Unix use forward slashes
- Empty directory entries (e.g., `jdk-25.0.3/`) are included in the enumeration but won't match because they end with `/` not the target filename

## Decisions

- Return `null` instead of throwing an exception when no match is found — this allows callers to handle the "not found" case gracefully without try-catch
- Use `endsWith("/" + name)` instead of splitting the path and comparing components — this is simpler and handles arbitrary depth
- The method is `static` and stateless — no instance state, no thread safety concerns

## Open Questions

- Should we add a `findAllZipEntries()` method that returns all matches instead of just the first one? This would be useful if callers need to compare entries across versions.
