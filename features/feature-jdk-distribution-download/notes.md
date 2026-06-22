# Notes

## Observations

- The Adoptium API is free and requires no authentication — this is a major advantage over other JDK distribution
  sources
- The download uses a `.part` file that is atomically renamed on completion — this ensures partial downloads are never
  mistaken for complete ones
- The `RestClient` is configured with `setInstanceFollowRedirects(true)` because Adoptium URLs often redirect to GitHub
  releases

## Decisions

- Use virtual threads instead of a fixed thread pool — this allows many concurrent downloads without thread exhaustion,
  and virtual threads are cheap to create/destroy
- Parse version strings into `[major, minor, security]` arrays — this allows flexible matching (e.g., requesting "21"
  matches any 21.x.y patch)
- The `parseVersion()` method strips the `jdk-` prefix and `+nn` build suffix — this handles the various version formats
  returned by the Adoptium API

## Open Questions

- Should we add a timeout for the Adoptium API queries? The current code doesn't set one, so a slow or unresponsive API
  could cause indefinite hangs.
- The `resolveBinary()` method iterates through up to 300 releases (6 pages × 50 per page). Should we limit this to
  avoid excessive API calls?
