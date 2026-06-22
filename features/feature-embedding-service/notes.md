# Notes

## Observations

- The service is extremely simple — just 30 lines of code — but the prefix convention is critical for embedding quality
- There are no unit tests because the service has no logic to test — it's pure delegation

## Decisions

- The prefixes are hard-coded constants (`PASSAGE_PREFIX`, `QUERY_PREFIX`) rather than configuration — this is because they are a property of the model family, not a deployment concern
