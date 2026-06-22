# Notes

## Observations

- The repository layer is very simple — just three interfaces with minimal custom queries.
- The `findAllVersionStringsOrderByMajorDesc()` query uses JPQL and orders by the denormalized `major`, `minor`, and
  `security` fields — this is more efficient than ordering by the `version` string.

## Decisions

- Use Spring Data JPA's derived query methods (`findByVersion`, `findByJdkVersionAndQualifiedId`) instead of `@Query` —
  this is cleaner and more maintainable.
- The `JdkDocChunkRepository` has no custom queries — it's just a basic CRUD repository.
