# Notes

## Observations

- The APP_ID is a UUID generated once per application instance — this can be used for request tracing or logging correlation.
- The `BufferingApplicationStartup` is configured to only capture `spring.beans.instantiate` startup steps — this limits the buffer usage since bean instantiation is the most expensive part of Spring Boot startup.

## Decisions

- The buffer size is hardcoded to 2048 — this is a reasonable default for most applications.
- The startup filter is hardcoded to `spring.beans.instantiate` — this ensures the buffer only captures the most important startup metric.
