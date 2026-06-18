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

Log file paths use cross-platform defaults:

- Directory: `./logs` (override with `LOGGING_PATH_DIRECTORY`)
- File: `{dir}/{spring.application.name}/output.log`
- Max backup files: `5` (override with `LOGGING_MAX_BACKUP_FILES`)

The default path `./logs` is resolved relative to the working directory and works on both Linux/Unix and Windows.
