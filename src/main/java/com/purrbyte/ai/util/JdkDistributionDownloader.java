package com.purrbyte.ai.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.purrbyte.ai.model.Progress;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Downloads full JDK distributions from Adoptium/Temurin so that arbitrary JDK versions (not just the
 * running one) can be documented. The distribution's {@code lib/src.zip} is the complete, compilable
 * source the doclet needs.
 *
 * <p>For a requested version it queries the Adoptium API to resolve the binary for the current OS and
 * architecture, then downloads it asynchronously (virtual threads) reporting progress. Downloads are
 * cached: an already-present archive is reused.
 */
@Slf4j
@Component
public class JdkDistributionDownloader {

    private static final String ADOPTIUM_BASE = "https://api.adoptium.net/v3";
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 6;
    private static final int DOWNLOAD_BUFFER = 1 << 16;

    private final Executor downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final String downloadDirectory;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public JdkDistributionDownloader(@Value("${jdk.distribution.download.directory}") String downloadDirectory, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.downloadDirectory = downloadDirectory;
        this.jsonMapper = jsonMapper;
        this.restClient = builder.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    @Override
                    protected void prepareConnection(java.net.@NonNull HttpURLConnection connection, @NonNull String httpMethod) throws java.io.IOException {
                        super.prepareConnection(connection, httpMethod);
                        connection.setInstanceFollowRedirects(true);
                    }
                })
                .build();
    }

    /**
     * Downloads the JDK distribution for the given version, for the current OS and architecture.
     *
     * @param version          requested JDK version (e.g. "21", "21.0.11")
     * @param progressCallback callback reporting {@link Progress#MODULE_DOWNLOAD} progress (maybe null)
     * @return future with the path to the downloaded archive (cached on later calls)
     */
    public CompletableFuture<Path> downloadDistribution(String version, Consumer<Progress> progressCallback) {
        String os = mapOs(System.getProperty("os.name"));
        String arch = mapArch(System.getProperty("os.arch"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                AdoptiumPackage pkg = resolveBinary(version, os, arch)
                        .orElseThrow(() -> new IOException(
                                "No Adoptium JDK binary found for version " + version + " (" + os + "/" + arch + ")"));
                Path targetDir = Path.of(downloadDirectory);
                Path targetFile = targetDir.resolve(pkg.name());
                if (Files.exists(targetFile)) {
                    log.info("JDK distribution already downloaded at {}", targetFile);
                    return targetFile;
                }
                Files.createDirectories(targetDir);
                Path partFile = targetDir.resolve(pkg.name() + ".part");
                long total = pkg.size();
                try (InputStream input = restClient.get().uri(URI.create(pkg.link())).retrieve().body(InputStream.class);
                     OutputStream output = Files.newOutputStream(partFile)) {
                    if (input == null) {
                        throw new IOException("Empty response from server");
                    }
                    byte[] buffer = new byte[DOWNLOAD_BUFFER];
                    long written = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        written += read;
                        if (total > 0 && progressCallback != null) {
                            double progress = Math.min(written / (double) total * 100.0, 100.0);
                            progressCallback.accept(Progress.of(progress, Progress.MODULE_DOWNLOAD));
                        }
                    }
                }
                Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("JDK distribution downloaded to {}", targetFile);
                return targetFile;
            } catch (Exception e) {
                throw new CompletionException(new IOException("Failed to download JDK distribution for version: " + version, e));
            }
        }, downloadExecutor);
    }

    /**
     * Resolves the Adoptium JDK binary (link, name, and size) for a version + OS + architecture by
     * paging through the GA feature releases and matching the requested major/minor/security.
     */
    Optional<AdoptiumPackage> resolveBinary(String version, String os, String arch) {
        int[] req = parseVersion(version);
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = ADOPTIUM_BASE + "/assets/feature_releases/" + req[0] + "/ga"
                    + "?architecture=" + arch + "&heap_size=normal&image_type=jdk&jvm_impl=hotspot"
                    + "&os=" + os + "&vendor=eclipse&page=" + page + "&page_size=" + PAGE_SIZE + "&sort_order=DESC";
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                break;
            }
            AdoptiumRelease[] releases = jsonMapper.readValue(body, AdoptiumRelease[].class);
            if (releases.length == 0) {
                break;
            }
            for (AdoptiumRelease release : releases) {
                if (!matchesVersion(release.versionData(), req)) {
                    continue;
                }
                if (release.binaries() == null) {
                    continue;
                }
                for (AdoptiumBinary binary : release.binaries()) {
                    if ("jdk".equals(binary.imageType()) && os.equals(binary.os()) && arch.equals(binary.architecture())
                            && binary.pkg() != null && binary.pkg().link() != null) {
                        return Optional.of(binary.pkg());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parses a version string into {@code [major, minor, security]}, where minor/security are {@code -1}
     * when not specified. Strips a leading {@code jdk-} prefix and any build suffix ({@code +nn}).
     */
    static int[] parseVersion(String version) {
        String clean = version.startsWith("jdk-") ? version.substring(4) : version;
        clean = clean.split("\\+")[0];
        String[] parts = clean.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
        int security = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
        return new int[]{major, minor, security};
    }

    /**
     * Returns true if the release's version matches the requested {@code [major, minor, security]}
     * (minor/security are only compared when requested, i.e., not {@code -1}).
     */
    static boolean matchesVersion(VersionData versionData, int[] req) {
        if (versionData == null) {
            return false;
        }
        if (versionData.major() != req[0]) {
            return false;
        }
        if (req[1] >= 0 && versionData.minor() != req[1]) {
            return false;
        }
        return req[2] < 0 || versionData.security() == req[2];
    }

    /**
     * Maps a {@code os.name} value to the Adoptium OS token.
     */
    static String mapOs(String osName) {
        String os = osName.toLowerCase();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "mac";
        }
        return "linux";
    }

    /**
     * Extracts the major version number from a version string.
     */
    @SuppressWarnings("JavaExistingMethodCanBeUsed")
    public static int extractMajorVersion(String version) {
        String cleanVersion = version.startsWith("jdk-") ? version.substring(4) : version;
        String[] parts = cleanVersion.split("\\.");
        return Integer.parseInt(parts[0]);
    }

    /**
     * Maps a {@code os.arch} value to the Adoptium architecture token.
     */
    static String mapArch(String osArch) {
        return switch (osArch.toLowerCase()) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> osArch.toLowerCase();
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoptiumRelease(@JsonProperty("version_data") VersionData versionData,
                           java.util.List<AdoptiumBinary> binaries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VersionData(int major, int minor, int security) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoptiumBinary(String os, String architecture,
                          @JsonProperty("image_type") String imageType,
                          @JsonProperty("package") AdoptiumPackage pkg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoptiumPackage(String name, String link, long size) {
    }
}
