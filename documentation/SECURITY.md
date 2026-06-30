# Security Configuration

## Actuator Endpoints

Actuator values are restricted by convention — do not expose sensitive data:

| Endpoint     | Setting     | Why                                   |
|--------------|-------------|---------------------------------------|
| Env vars     | `NEVER`     | Avoid leaking secrets                 |
| Config props | `NEVER`     | Avoid leaking secrets                 |
| Loggers      | `READ_ONLY` | Allow inspection without modification |
| Health       | Always show | Diagnostic only, no sensitive data    |

### Actuator Environment Variables

| Variable                        | Default     | Purpose                            |
|---------------------------------|-------------|------------------------------------|
| `ACTUATOR_HEALTH_SHOW_DETAILS`  | `ALWAYS`    | Show health details by default     |
| `ACTUATOR_LOGGERS_ACCESS`       | `READ_ONLY` | Allow reading loggers, not writing |
| `ACTUATOR_ENDPOINTS_WEB_EXPOSE` | `*` (all)   | Control web-exposed endpoints      |
| `ACTUATOR_ENDPOINTS_EXPOSE`     | `*` (all)   | Control JMX-exposed endpoints      |
| `ACTUATOR_INFO_BUILD_ENABLED`   | `true`      | Expose build info endpoint         |
| `ACTUATOR_INFO_JAVA_ENABLED`    | `true`      | Expose Java info endpoint          |

Observations annotations are enabled by default.

Metrics export is disabled by default.

## Logging Paths

Log file paths use cross-platform defaults:

- Directory: `./logs` (override with `LOGGING_PATH_DIRECTORY`)
- File: `{dir}/{spring.application.name}/output.log`
- Max backup files: `5` (override with `LOGGING_MAX_BACKUP_FILES`)
- Max history days: `7` (override with `LOGGING_MAX_HISTORY_DAYS`)
- Max file size: `10MB` (override with `LOGGING_MAX_FILE_SIZE`)

The default path `./logs` is resolved relative to the working directory and works on both Linux/Unix and Windows.
