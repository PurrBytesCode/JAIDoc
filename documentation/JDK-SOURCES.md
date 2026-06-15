# JDK Source Downloader

## Purpose

`JdkSourceDownloader` downloads JDK source-code archives from the official OpenJDK GitHub repositories. For a given JDK
version it resolves the correct repository and release tag, downloads the source ZIP asynchronously (virtual threads),
and reports progress through a `Consumer<Progress>` callback.

## Repositories

The downloader uses the official OpenJDK upstream repositories, each with its own tag format:

| JDK Version | Repository       | Tag Format         | Example          |
|-------------|------------------|--------------------|------------------|
| 8           | `openjdk/jdk8u`  | `jdk8uXXX-ga`      | `jdk8u492-ga`    |
| 11          | `openjdk/jdk11u` | `jdk-{version}-ga` | `jdk-11.0.28-ga` |
| 17          | `openjdk/jdk17u` | `jdk-{version}-ga` | `jdk-17.0.13-ga` |
| 21          | `openjdk/jdk21u` | `jdk-{version}-ga` | `jdk-21.0.11-ga` |
| 25          | `openjdk/jdk25u` | `jdk-{version}-ga` | `jdk-25.0.1-ga`  |

## Current Versions

<!-- BEGIN: JDK Current Versions -->
The following update versions are currently available
on [Oracle's JDK Release Notes](https://www.oracle.com/java/technologies/javase/jdk-relnotes-index.html):

### JDK 8

Update versions: 8u491, 8u481, 8u471, 8u461, 8u451, 8u441, 8u431, 8u421, 8u411, 8u401, 8u391, 8u381, 8u371, 8u361,
8u351, 8u341, 8u333, 8u331, 8u321, 8u311, 8u301, 8u291, 8u281, 8u271, 8u261, 8u251, 8u241, 8u231, 8u221, 8u212, 8u211,
8u202, 8u201, 8u192, 8u191, 8u181, 8u172, 8u171, 8u162, 8u161, 8u152, 8u151, 8u144, 8u141, 8u131, 8u121, 8u112, 8u111,
8u102, 8u101, 8u92, 8u91, 8u77, 8u74, 8u73, 8u72, 8u71, 8u66, 8u65, 8u60, 8u51, 8u45, 8u40, 8u31, 8u25, 8u20, 8u11, 8u5

### JDK 11

Update versions: 11.0.31, 11.0.30, 11.0.29, 11.0.28, 11.0.27, 11.0.26, 11.0.25, 11.0.24, 11.0.23, 11.0.22, 11.0.21,
11.0.20, 11.0.19, 11.0.18, 11.0.17, 11.0.16, 11.0.15, 11.0.14, 11.0.13, 11.0.12, 11.0.11, 11.0.10, 11.0.9, 11.0.8,
11.0.7, 11.0.6, 11.0.5, 11.0.4, 11.0.3, 11.0.2, 11.0.1

### JDK 17

Update versions: 17.0.19, 17.0.18, 17.0.17, 17.0.16, 17.0.15, 17.0.14, 17.0.13, 17.0.12, 17.0.11, 17.0.10, 17.0.9,
17.0.8, 17.0.7, 17.0.6, 17.0.5, 17.0.4, 17.0.3, 17.0.2, 17.0.1

### JDK 21

Update versions: 21.0.11, 21.0.10, 21.0.9, 21.0.8, 21.0.7, 21.0.6, 21.0.5, 21.0.4, 21.0.3, 21.0.2, 21.0.1

### JDK 25

Update versions: 25.0.3, 25.0.2, 25.0.1
<!-- END: JDK Current Versions -->

## Usage

```java
// Download a specific version (async, returns a Path to the zip)
CompletableFuture<Path> future = downloader.downloadSource("17.0.1", p -> System.out.println(p.module() + ": " + p.percentage() + "%"));
```

## Key Behaviors

- **Async execution** — Uses a virtual thread executor (`Executors.newVirtualThreadPerTaskExecutor()`)
- **Progress callback** — Optional `Consumer<Progress>` for progress reporting; each update includes a phase name (
  download/extract/javadoc) and percentage (0.0–100.0)
- **Version normalization** — Accepts version strings in various formats, validates format
- **Multi-repo support** — Automatically selects the correct GitHub repository based on the JDK version

## Configuration

Directory is configurable via:

```yaml
jdk:
  source:
    download:
      directory: ${JDK_SOURCE_DOWNLOAD_ZIP:./jdk-sources}
```

## Version Format

Supported version formats:

- **JDK 8**: `8`, `8u412`, `8.0.412` (normalized to `8u412` for downloads)
- **Modern JDK**: `11`, `11.0.28`, `17`, `17.0.13`, `21`, `21.0.11`, `25`, `25.0.1`

Versions with `jdk-` prefix (e.g., `jdk-8u412`, `jdk-17.0.13`) are normalized by stripping the prefix.

## Updating the Version List

Only update this section when explicitly asked. Do not proactively update it.

To update with the latest JDK update versions:

1. Run `scripts\update-jdk-versions.ps1` to fetch the latest versions from Oracle's release notes
2. The script outputs markdown content for the entire section
3. Replace ONLY the text between `<!-- BEGIN: JDK Current Versions -->` and `<!-- END: JDK Current Versions -->` (
   inclusive of the markers themselves)
4. Do NOT change any other section of this file

The script fetches versions from:

- JDK 8: `https://www.oracle.com/java/technologies/javase/8u-relnotes.html`
- JDK 11: `https://www.oracle.com/java/technologies/javase/11u-relnotes.html`
- JDK 17: `https://www.oracle.com/java/technologies/javase/17u-relnotes.html`
- JDK 21: `https://www.oracle.com/java/technologies/javase/21u-relnotes.html`
- JDK 25: `https://www.oracle.com/java/technologies/javase/25u-relnotes.html`
