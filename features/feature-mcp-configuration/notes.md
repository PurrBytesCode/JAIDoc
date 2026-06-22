# Notes

## Observations

- The MCP configuration is extremely simple — just two bean methods.
- The `MethodToolCallbackProvider` uses reflection to discover `@Tool`-annotated methods — no explicit method
  registration is needed.

## Decisions

- Use `MethodToolCallbackProvider` instead of programmatically creating tool callbacks — this is cleaner and leverages
  Spring AI's built-in reflection mechanism.
