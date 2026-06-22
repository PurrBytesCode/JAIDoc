# Notes

## Observations

- The success detection in `runJavadocDoclet()` is based on the presence of `index.json`, not the javadoc exit code —
  this is critical because the doclet runs after type attribution, so missing generated symbols can leave a non-zero
  exit code even though valid output was produced
- The progress ticker during javadoc is a rough estimate (5% to 100% over 100 ticks) — javadoc doesn't provide actual
  progress, so this is a best-effort approximation
- The `zipVersion()` method compresses the version directory and then deletes the original — this saves disk space but
  means the extracted directory is never available after compression

## Decisions

- Use virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) instead of a fixed thread pool — this allows many
  concurrent pipelines without thread exhaustion
- Set `--Xmaxerrs 100000 --Xmaxwarns 100000` because the JDK source references build-time-generated symbols that may be
  absent, and the default diagnostic limits would cause javadoc to abort
- The `isVersionGenerated()` method checks for `index.json` inside the ZIP (using `ZIPHelper.findZipEntry`), not just
  the presence of the ZIP — this ensures the ZIP is a valid documentation archive

## Open Questions

- Should we add a checksum verification step after downloading the JDK archive? Currently there's no integrity check.
- The progress ticker could be improved by reading javadoc's diagnostic output to determine actual progress (e.g.,
  counting modules processed).
