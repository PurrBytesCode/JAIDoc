# Security Configuration

## Actuator Endpoints

Actuator values are restricted by convention — do not expose sensitive data:

| Endpoint     | Setting     | Why                                   |
|--------------|-------------|---------------------------------------|
| Env vars     | `NEVER`     | Avoid leaking secrets                 |
| Config props | `NEVER`     | Avoid leaking secrets                 |
| Loggers      | `READ_ONLY` | Allow inspection without modification |
| Health       | Always show | Diagnostic only, no sensitive data    |

Metrics export is disabled by default.

## Logging Paths

Log file paths use Linux-style defaults:

- Directory: `/dev/logs` (override with `LOGGING_PATH_DIRECTORY`)
- File: `{dir}/{spring.application.name}/output.log`

On Windows, these paths don't exist — set `LOGGING_PATH_DIRECTORY` to a valid directory.
