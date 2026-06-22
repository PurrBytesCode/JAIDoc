# Data Flow

## Overview

The JDK Distribution Downloader queries the Adoptium/Temurin API to find and download a JDK binary archive for a
specific version, OS, and architecture. Downloads are cached — if the archive already exists locally, it's reused.
Progress is reported via a callback during the download.

## Sequence Diagram

```
JdkDistributionDownloader.downloadDistribution(version)
    │
    ├──→ mapOs(os.name) → "windows"
    │     mapArch(os.arch) → "x64"
    │
    ├──→ resolveBinary(version, os, arch)
    │     │
    │     ├──→ parseVersion("21.0.11") → [21, 0, 11]
    │     │
    │     ├──→ for page in [0..5]:
    │     │     └── GET /v3/assets/feature_releases/21/ga?page=0&page_size=50
    │     │           └── Parse AdoptiumRelease[]
    │     │           └── matchesVersion(release.versionData, [21, 0, 11])
    │     │
    │     └──→ AdoptiumPackage(name, link, size)
    │
    ├──→ Files.exists(targetFile) → true: return cached file
    │
    └──→ Download:
          └── GET archive link → InputStream
              └── Write to .part file with 64KB buffer
                  └── progressCallback.accept(Progress.of(progress, MODULE_DOWNLOAD))
                      └── Move .part → target file
```

## Data Models

### Input

```json
{
  "version": "21.0.11",
  "os": "windows",
  "arch": "x64",
  "progressCallback": "Consumer<Progress> — may be null"
}
```

### Output

```
Path — the local file path to the downloaded archive, or an exception if the download fails
```

### API Response (Adoptium)

```json
{
  "version_data": {
    "major": 21,
    "minor": 0,
    "security": 11
  },
  "binaries": [
    {
      "os": "windows",
      "architecture": "x64",
      "image_type": "jdk",
      "jvm_impl": "hotspot",
      "package": {
        "name": "jdk-21.0.11_windows-x64_bin.zip",
        "link": "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B7/OpenJDK21U-jdk_x64_windows_21.0.11_7.zip",
        "size": 189456123
      }
    }
  ]
}
```

## Error States

- `IOException("No Adoptium JDK binary found...")` — No matching binary found for the requested version/OS/arch
- `IOException("Empty response from server")` — The Adoptium API returned no content
- `IOException("Failed to download JDK distribution...")` — Network error during download (wrapped in
  `CompletionException`)
- `CompletionException` — Any exception during the async download is wrapped
