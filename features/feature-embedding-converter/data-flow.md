# Data Flow

## Overview

The embedding converter serializes 384-dimensional float arrays to byte arrays for SQLite BLOB storage, and deserializes
them back when reading from the database. It is a stateless, thread-safe utility used by Hibernate during entity
persistence and retrieval.

## Sequence Diagram

```
JdkDocChunk.embedding (float[384])
    │
    ├──→ FloatArrayConverter.convertToDatabaseColumn()
    │        └── byte[1536] (384 × 4 bytes)
    │              ↓
    │         SQLite BLOB column
    │
    └──← FloatArrayConverter.convertToEntityAttribute()
            └── float[384]
                  ↑
            SQLite BLOB column
```

## Data Models

### Input (float[] from Java)

```
float[384] = {0.123f, -0.456f, 0.789f, ...}  // 384 float32 values
```

### Output (byte[] for SQLite)

```
byte[1536] = [0x3f, 0x9e, 0x47, 0xab, 0xbf, 0xb6, ...]  // 384 × 4 bytes (big-endian)
```

## Error States

- `IllegalArgumentException` — None; the converter never throws checked exceptions
- `OutOfMemoryError` — If the JVM cannot allocate 1536 bytes for the byte buffer (extremely unlikely)
