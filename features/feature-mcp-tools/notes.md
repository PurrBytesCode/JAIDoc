# Notes

## Observations

- The MCP tools are very simple — just two methods in JavaDocMCP and one placeholder in SpringBootMCP.
- The `searchJavadoc()` method defaults `topK` to 10 if not specified or <= 0 — this is important because AI models may
  not always specify the parameter.

## Decisions

- The `@ToolParam` annotations provide descriptions that appear in the MCP tool schema — this helps AI models understand
  what each parameter means and how to use it.
- The SpringBootMCP tool is a placeholder — it returns a stub response with a TODO comment. This is intentional; the
  actual implementation will come when the Spring Boot adoc parsing pipeline is ready.

## Open Questions

- Should we add a `searchAllVersions` tool that searches across all versions? Currently the search is limited to one
  version at a time.
- Should the `searchJavadoc()` method return more context — e.g., the full Javadoc comment instead of just the chunk
  text?
