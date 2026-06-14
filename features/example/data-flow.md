# Data Flow

## Overview

Describe the end-to-end data flow for this feature. What inputs does it receive? What outputs does it produce?

## Sequence Diagram

```
Client → XxxService → XxxUtil → External API
                    ↑             ↓
                    └── XxxConfig ←──
```

## Data Models

### Input

```json
{
  "version": "25.0.3",
  "subpackages": ["java", "jdk"]
}
```

### Output

```json
{
  "path": "data/25.0.3/index.json",
  "status": "completed",
  "progress": 1.0
}
```

## Error States

- `404 Not Found` — Version not available in the downstream service
- `429 Too Many Requests` — Rate limit exceeded, retry with backoff
- `500 Internal Server Error` — Service unavailable, fallback to cache
