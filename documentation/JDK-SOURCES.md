# JDK Source Downloader

## Purpose

`JdkSourceDownloader` downloads JDK source code archives from the OpenJDK GitHub repository. This is used to document
Java 8 sources via the `javadoc` CLI (see [DOCLET.md](DOCLET.md) for the JDK 8 bug workaround).

## Usage

```java
// List available versions
List<String> versions = downloader.listAvailableVersions();

// Download a specific version (async, returns a Path to the zip)
CompletableFuture<Path> future = downloader.downloadSource("17.0.1", progress -> System.out.println(progress + "%"));
```

## Key Behaviors

- **Async execution** — Uses a virtual thread executor (`Executors.newVirtualThreadPerTaskExecutor()`)
- **Progress callback** — Optional `Consumer<Double>` for progress reporting
- **Version normalization** — Accepts `17.0.1` or `jdk-17.0.1`, strips the `jdk-` prefix if present, validates format
- **GitHub API** — `listAvailableVersions()` fetches from `https://api.github.com/repos/openjdk/jdk/tags` and parses tag
  names via regex

## Configuration

Directory is configurable via:

```yaml
jdk:
  source:
    download:
      directory: ${JDK_SOURCE_DOWNLOAD_ZIP:./jdk-sources}
```

## Version Format

Only versions matching `^\d+(\.\d+)*$` are accepted (e.g., `8`, `11.0.2`, `17.0.1`). Versions with `jdk-` prefix are
normalized by stripping it.
