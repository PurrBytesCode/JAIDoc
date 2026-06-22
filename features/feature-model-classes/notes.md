# Notes

## Observations

- `IngestStatus` has three values but `FAILED` is never set by the codebase — the `IngestionService` only sets `READY` on success and doesn't have an error path. This means `INGESTING` effectively means "not yet ready" (either processing or failed).
- The progress phases use static string constants (`MODULE_DOWNLOAD`, `MODULE_EXTRACT`, `MODULE_JAVADOC`) instead of an enum — this was likely a design choice to allow extensibility, but it means callers have to match strings rather than enum values.
- `JdkSearchResult` is `final` but has `@Setter` from Lombok — this makes the class immutable from outside but allows internal mutation. This is an unusual pattern; typically a `final` class with getters only would be preferred for a DTO.

## Decisions

- `IngestStatus` uses Javadoc comments to describe each enum value's meaning, which is good practice for domain types
- `Progress` uses a static factory method `of()` alongside the constructor for convenience — both do the same rounding
- `JdkSearchResult` uses `@Builder` for constructing instances with optional fields, which is common for search result DTOs

## Open Questions

- Should `IngestStatus.FAILED` be used when ingestion fails? The current code doesn't set it, but it would be useful for distinguishing between "still processing" and "processing failed".
- Should `Progress` phases be an enum instead of string constants for type safety?
