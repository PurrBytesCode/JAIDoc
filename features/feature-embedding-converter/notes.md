# Notes

## Observations

- The converter is extremely simple — no custom logic, just ByteBuffer byte order conversion
- The 384 dimension is implied by the byte array size (384 × 4 = 1536 bytes), not explicitly validated
- The converter handles null safely in both directions, which is important for embeddings that might be null before indexing

## Decisions

- Use `ByteBuffer.allocate()` (not `wrap()`) in `convertToDatabaseColumn` because the float[] needs to be copied into the buffer before converting to byte[]
- Use `ByteBuffer.wrap()` in `convertToEntityAttribute` because we want a view of the existing byte array, not a copy
- The converter is stateless — no instance variables, no thread safety concerns

## Open Questions

- Should we add a dimension validation check to ensure the byte array is exactly 1536 bytes (384 × 4)? A mismatch would indicate model version drift.
