package com.purrbyte.ai.service;

import com.purrbyte.ai.model.dto.Progress;
import com.purrbyte.ai.repository.JdkVersionRepository;
import com.purrbyte.ai.util.JdkDistributionDownloader;
import com.purrbyte.ai.util.ZIPHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Generates JSON documentation for the JDK using the {@code JsonDoclet}.
 *
 * <p>The source is a complete {@code lib/src.zip} (the OpenJDK GitHub source tree is incomplete because
 * many classes — e.g., the {@code java.nio} buffers — are generated at build time). When the requested
 * version matches the running JDK, its local {@code lib/src.zip} is used; otherwise the distribution is
 * downloaded from Adoptium (see {@link JdkDistributionDownloader}) and its {@code lib/src.zip} extracted.
 *
 * <p>Only modular JDKs (11+) are supported. The {@code javadoc} that runs the doclet (configurable via
 * {@code doclet.javadoc.home}, default = the running JDK) must be a JDK 17+ (the doclet needs Jackson 3)
 * whose major version is &gt;= the documented version.
 */
@Slf4j
@Service
public class DocumentationService {

    private static final int JAVADOC_MAX_DIAGNOSTICS = 100_000;
    private static final int MIN_MODULAR_MAJOR = 11;
    private static final int MIN_JAVADOC_MAJOR = 17;

    private final Executor documentationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final JdkDistributionDownloader jdkDistributionDownloader;
    private final JdkVersionRepository jdkVersionRepository;
    private final Path workDirectory;
    private final Path outputDirectory;
    private final Path docletDirectory;
    private final Path javadocHome;
    private final long javadocTimeoutSeconds;
    private final List<String> configuredModules;

    public DocumentationService(
            JdkDistributionDownloader jdkDistributionDownloader,
            JdkVersionRepository jdkVersionRepository,
            @Value("${doclet.work.directory}") Path workDirectory,
            @Value("${data.directory}") Path outputDirectory,
            @Value("${doclet.modules:}") String modulesCsv,
            @Value("${doclet.jar.directory:doclet}") Path docletDirectory,
            @Value("${doclet.javadoc.home:}") String javadocHome,
            @Value("${doclet.javadoc.timeout:600}") long javadocTimeoutSeconds) {
        this.jdkDistributionDownloader = jdkDistributionDownloader;
        this.jdkVersionRepository = jdkVersionRepository;
        this.workDirectory = workDirectory;
        this.outputDirectory = outputDirectory;
        this.docletDirectory = docletDirectory;
        this.javadocHome = (javadocHome == null || javadocHome.isBlank())
                ? Path.of(System.getProperty("java.home"))
                : Path.of(javadocHome);
        this.javadocTimeoutSeconds = javadocTimeoutSeconds;
        this.configuredModules = parseModules(modulesCsv);
    }

    /**
     * Generates JDK documentation for the specified version using the JsonDoclet.
     *
     * <p>The pipeline is:
     * <ol>
     *   <li>Obtain a complete {@code lib/src.zip}: the running JDK's own when the version matches,
     *       otherwise download the Adoptium distribution and extract its {@code lib/src.zip}</li>
     *   <li>Extract the source archive — reports {@link Progress#MODULE_EXTRACT}</li>
     *   <li>Run javadoc with the JsonDoclet in module mode and copy to the output directory —
     *       reports {@link Progress#MODULE_JAVADOC}</li>
     * </ol>
     *
     * @param version          JDK version (e.g. "21.0.11", "25.0.3"); must be a modular JDK (11+)
     * @param progressCallback callback for progress updates (each phase reports its own 0-100%)
     * @return future with the path to the generated documentation directory in the output directory
     */
    public CompletableFuture<Path> generateJdkDocumentation(String version, Consumer<Progress> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        int requestedMajor = JdkDistributionDownloader.extractMajorVersion(version);
                        validateRequest(version, requestedMajor);
                        Consumer<Double> extractCallback = p -> {
                            if (progressCallback != null) {
                                progressCallback.accept(Progress.of(p, Progress.MODULE_EXTRACT));
                            }
                        };
                        Path extractDir = workDirectory.resolve("jdk-sources").resolve(version);
                        boolean sourceWasExtracted;
                        if (Files.exists(extractDir)) {
                            log.info("Source already extracted at {}", extractDir);
                            sourceWasExtracted = false;
                        } else {
                            Path archive = downloadDistribution(version, progressCallback);
                            sourceWasExtracted = extractSourceZip(extractSrcZipFromArchive(archive, version), version, extractCallback);
                        }
                        List<String> modules = resolveModules(extractDir);
                        log.info("Documenting JDK {} ({} modules)", version, modules.size());
                        return runJavadocDoclet(extractDir, version, modules, progressCallback, sourceWasExtracted);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, documentationExecutor)
                .exceptionally(ex -> {
                    log.error("JDK documentation generation failed for version {}: {}", version, ex.getMessage());
                    throw new CompletionException(ex);
                });
    }

    /**
     * Validates that the requested version is documentable: it must be a modular JDK (11+), and the
     * javadoc JDK (configured or running) must be 17+ (the doclet needs Jackson 3) and not older than
     * the requested major (a newer tool reads an older source, not the other way around).
     */
    private void validateRequest(String version, int requestedMajor) throws IOException {
        if (requestedMajor < MIN_MODULAR_MAJOR) {
            throw new IOException("Documenting JDK " + version + " is not supported yet: only modular JDKs ("
                    + MIN_MODULAR_MAJOR + "+) are handled. JDK 8 support is planned.");
        }
        int javadocMajor = javadocJdkMajor();
        if (javadocMajor >= 0 && javadocMajor < MIN_JAVADOC_MAJOR) {
            throw new IOException("The javadoc JDK (" + javadocHome + ", major " + javadocMajor + ") must be "
                    + MIN_JAVADOC_MAJOR + " or newer; the doclet requires Jackson 3 (JDK " + MIN_JAVADOC_MAJOR + "+).");
        }
        if (javadocMajor >= 0 && javadocMajor < requestedMajor) {
            throw new IOException("The javadoc JDK (major " + javadocMajor + ") cannot document newer JDK "
                    + version + " source. Configure doclet.javadoc.home with a JDK whose major is >= " + requestedMajor + ".");
        }
    }

    /**
     * Determines the major version of the javadoc JDK: the runtime feature version when it is the
     * running JDK, otherwise parsed from the JDK home's {@code release} file. Returns {@code -1} when
     * it cannot be determined (validation is then skipped).
     */
    private int javadocJdkMajor() {
        if (javadocHome.equals(Path.of(System.getProperty("java.home")))) {
            return Runtime.version().feature();
        }
        Path release = javadocHome.resolve("release");
        if (Files.exists(release)) {
            try {
                for (String line : Files.readAllLines(release)) {
                    if (line.startsWith("JAVA_VERSION=")) {
                        String value = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                        return JdkDistributionDownloader.extractMajorVersion(value);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not read JDK version from {}: {}", release, e.getMessage());
            }
        }
        return -1;
    }

    /**
     * Locates the {@code lib/src.zip} of the running JDK.
     */
    private Path localSrcZip() throws IOException {
        Path srcZip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
        if (!Files.exists(srcZip)) {
            throw new IOException("JDK source archive not found at " + srcZip
                    + " — this JDK distribution does not ship lib/src.zip.");
        }
        return srcZip;
    }

    /**
     * Downloads the Adoptium JDK distribution for the version, unwrapping the async failure.
     */
    private Path downloadDistribution(String version, Consumer<Progress> progressCallback) throws IOException {
        try {
            return jdkDistributionDownloader.downloadDistribution(version, progressCallback).join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("JDK distribution download failed for " + version, cause);
        }
    }

    /**
     * Extracts {@code lib/src.zip} out of a downloaded JDK distribution archive ({@code .zip} on
     * Windows, {@code .tar.gz} on Linux/macOS) into {@code <work>/jdk-sources/<version>-src.zip}.
     * Idempotent: an already-extracted file is reused.
     */
    private Path extractSrcZipFromArchive(Path archive, String version) throws IOException {
        Path destSrcZip = workDirectory.resolve("jdk-sources").resolve(version + "-src.zip");
        if (Files.exists(destSrcZip)) {
            return destSrcZip;
        }
        Files.createDirectories(destSrcZip.getParent());
        String name = archive.getFileName().toString().toLowerCase();
        boolean found;
        if (name.endsWith(".zip")) {
            found = extractZipEntry(archive, destSrcZip);
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            found = extractTarGzEntry(archive, destSrcZip);
        } else {
            throw new IOException("Unsupported JDK archive type: " + archive.getFileName());
        }
        if (!found) {
            throw new IOException("lib/src.zip not found inside " + archive.getFileName()
                    + " — the distribution may not include sources.");
        }
        log.info("Extracted lib/src.zip from {} to {}", archive.getFileName(), destSrcZip);
        return destSrcZip;
    }

    /**
     * Copies the first zip entry whose name ends with {@code "lib/src.zip"} to {@code dest}.
     */
    private boolean extractZipEntry(Path archive, Path dest) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().replace('\\', '/').endsWith("lib/src.zip")) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Copies the first tar.gz entry whose name ends with {@code "lib/src.zip"} to {@code dest}.
     */
    private boolean extractTarGzEntry(Path archive, Path dest) throws IOException {
        try (InputStream fileInput = Files.newInputStream(archive);
             GzipCompressorInputStream gzip = new GzipCompressorInputStream(fileInput);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().replace('\\', '/').endsWith("lib/src.zip")) {
                    Files.copy(tar, dest, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extracts a source zip into {@code <work>/jdk-sources/<version>} with zip-slip protection.
     * The extraction is idempotent: if the directory already exists, it is reused.
     *
     * @return true if the source was newly extracted, false if the directory already existed
     */
    private boolean extractSourceZip(Path zipFile, String version, Consumer<Double> progressCallback) throws IOException {
        Path extractDir = workDirectory.resolve("jdk-sources").resolve(version);
        if (Files.exists(extractDir)) {
            log.info("The source has already been obtained at {}.", extractDir);
            return false;
        }
        Files.createDirectories(extractDir);
        int totalEntries;
        try (ZipFile zipFileObj = new ZipFile(zipFile.toFile())) {
            totalEntries = (int) zipFileObj.stream().filter(e -> !e.isDirectory()).count();
        }
        int processed = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path dest = extractDir.resolve(entry.getName()).normalize();
                if (!dest.startsWith(extractDir)) {
                    log.warn("Skipping zip-slip entry: {}", entry.getName());
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    processed++;
                    if (progressCallback != null && totalEntries > 0) {
                        progressCallback.accept(processed / (double) totalEntries * 100.0);
                    }
                }
                zis.closeEntry();
            }
        }
        log.info("JDK source extracted to: {}", extractDir);
        return true;
    }

    /**
     * Compresses the version directory into a ZIP file at {@code data/jdk/<version>.zip}
     * and deletes the original extracted directory. Idempotent: skips if ZIP already exists.
     *
     * @param versionDir the extracted version directory to compress (e.g. {@code data/jdk/25.0.3/})
     * @param version    the version string (used for ZIP filename)
     */
    void zipVersion(Path versionDir, String version) throws IOException {
        Path zipPath = versionDir.getParent().resolve(version + ".zip");
        if (Files.exists(zipPath)) {
            log.info("ZIP already exists at {}, skipping compression", zipPath);
            return;
        }
        log.info("Compressing {} into {}", versionDir, zipPath);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            try (var walk = Files.walk(versionDir)) {
                String versionPrefix = version + "/";
                walk.forEach(source -> {
                    try {
                        String entryName = versionDir.relativize(source).toString();
                        if (Files.isDirectory(source)) {
                            zos.putNextEntry(new ZipEntry(versionPrefix + entryName + "/"));
                            zos.closeEntry();
                        } else {
                            zos.putNextEntry(new ZipEntry(versionPrefix + entryName));
                            Files.copy(source, zos);
                            zos.closeEntry();
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        // Delete the original directory after successful compression
        try (var walk = Files.walk(versionDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
        }
        log.info("Compression complete: {}", zipPath);
    }

    /**
     * Resolves the modules to document: the configured list if any, otherwise every module found under
     * the source root (a directory is a module when it contains a {@code module-info.java}).
     */
    private List<String> resolveModules(Path moduleRoot) throws IOException {
        if (!configuredModules.isEmpty()) {
            return configuredModules;
        }
        List<String> modules = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleRoot)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && Files.exists(entry.resolve("module-info.java"))) {
                    modules.add(entry.getFileName().toString());
                }
            }
        }
        Collections.sort(modules);
        return modules;
    }

    /**
     * Runs javadoc with the JsonDoclet and copies the output to the data directory.
     * Preserves the javadoc-out temp directory for debugging purposes.
     *
     * @param moduleRoot         the extracted source directory
     * @param version            JDK version
     * @param modules            modules to document
     * @param progressCallback   callback for progress updates
     * @param sourceWasExtracted true if the source was newly extracted (allows cleanup)
     * @return path to the generated documentation directory in the output directory
     */
    private Path runJavadocDoclet(Path moduleRoot, String version, List<String> modules, Consumer<Progress> progressCallback, boolean sourceWasExtracted) throws IOException {
        if (modules.isEmpty()) {
            throw new IOException("No modules found to document under " + moduleRoot);
        }
        Path tempOutputDir = workDirectory.resolve("javadoc-out").resolve(version);
        Files.createDirectories(tempOutputDir);
        String osName = System.getProperty("os.name").toLowerCase();
        Path javadocBin = javadocHome.resolve("bin").resolve(osName.contains("win") ? "javadoc.exe" : "javadoc");
        if (!Files.exists(javadocBin)) {
            throw new IllegalStateException("javadoc binary not found at: " + javadocBin
                    + " (configure doclet.javadoc.home to a valid JDK home)");
        }
        List<String> command = new ArrayList<>();
        command.add(javadocBin.toString());
        String docletPath = resolveDocletPath();
        if (docletPath != null) {
            command.add("-docletpath");
            command.add(docletPath);
        }
        command.add("-doclet");
        command.add("com.purrbyte.ai.doclet.JsonDoclet");
        command.add("--module-source-path");
        command.add(moduleRoot.toString());
        command.add("--module");
        command.add(String.join(",", modules));
        command.add("-d");
        command.add(tempOutputDir.toString());
        command.add("--pretty");
        command.add("--doc-version");
        command.add(version);
        // The JDK source references build-time-generated symbols that may be absent; raise the
        // diagnostic limits so Javadoc still runs the doclet instead of aborting at the default cap.
        command.add("-Xmaxerrs");
        command.add(String.valueOf(JAVADOC_MAX_DIAGNOSTICS));
        command.add("-Xmaxwarns");
        command.add(String.valueOf(JAVADOC_MAX_DIAGNOSTICS));
        log.info("Executing javadoc: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        if (progressCallback != null) {
            progressCallback.accept(Progress.of(0, Progress.MODULE_JAVADOC));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[javadoc] {}", line);
            }
        } catch (IOException e) {
            log.warn("Error reading javadoc output: {}", e.getMessage());
        }
        boolean exited;
        try {
            exited = process.waitFor(javadocTimeoutSeconds, TimeUnit.SECONDS);
            if (progressCallback != null) {
                progressCallback.accept(Progress.of(100, Progress.MODULE_JAVADOC));
            }
        } catch (InterruptedException e) {
            if (progressCallback != null) {
                progressCallback.accept(Progress.of(-1, Progress.MODULE_JAVADOC));
            }
            Thread.currentThread().interrupt();
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc process interrupted", e);
        }
        if (!exited) {
            process.destroyForcibly();
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc process timed out after " + javadocTimeoutSeconds + " seconds");
        }
        int exitCode = process.exitValue();
        // The doclet runs only after type attribution; missing generated symbols can leave a non-zero
        // exit code even though valid output was produced. Treat the presence of index.json as a success.
        if (!Files.exists(tempOutputDir.resolve("index.json"))) {
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc did not produce index.json (exit code " + exitCode + ")");
        }
        if (exitCode != 0) {
            log.warn("javadoc exited with code {} but index.json was produced; continuing with partial documentation", exitCode);
        }
        Path versionDir = outputDirectory.resolve("jdk").resolve(version);
        Files.createDirectories(versionDir);
        try {
            copyDirectory(tempOutputDir, versionDir);
        } catch (IOException e) {
            throw new IOException("Failed to copy javadoc output to output directory: " + e.getMessage(), e);
        }
        // Delete the extracted source directory if it was newly created
        if (sourceWasExtracted && Files.exists(moduleRoot)) {
            try {
                deleteDirectory(moduleRoot);
            } catch (IOException e) {
                log.warn("Failed to clean up source dir {}: {}", moduleRoot, e.getMessage());
            }
        }
        zipVersion(versionDir, version);
        log.info("JDK documentation generated at {}", versionDir);
        return versionDir;
    }

    /**
     * Resolves the path to the doclet JAR in the configured doclet directory.
     *
     * <p>Looks for the JAR {@code JAIDoc-doclet.jar} in {@code doclet.jar.directory} (default
     * {@code doclet}). Returns null if not found — javadoc will then use its own classpath.
     */
    String resolveDocletPath() {
        try (var stream = Files.list(docletDirectory)) {
            return stream.filter(p -> p.getFileName().toString().equals("JAIDoc-doclet.jar"))
                    .findFirst()
                    .map(path -> {
                        log.debug("Doclet JAR resolved: {}", path);
                        return path.toString();
                    })
                    .orElse(null);
        } catch (IOException e) {
            log.debug("Failed to list doclet directory: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Copies all contents from the source directory to the destination directory.
     */
    private void copyDirectory(Path source, Path destination) throws IOException {
        try (var walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path dest = destination.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Deletes a directory and all its contents recursively.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    /**
     * Parses a comma-separated module list, trimming blanks. An empty value means "all modules".
     */
    private static List<String> parseModules(String modulesCsv) {
        if (modulesCsv == null || modulesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(modulesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Lists available JDK versions that have been generated and ingested.
     *
     * <p>Reads from the database (only versions with READY ingest status) instead of
     * scanning the filesystem, which is faster and more reliable.
     *
     * @return list of version strings ordered from the newest major to the oldest
     */
    public List<String> listAvailableVersions() {
        return jdkVersionRepository.findAllVersionStringsOrderByMajorDesc();
    }

    /**
     * Finds a version's documentation ZIP file under the output directory.
     * Searches recursively for a file named {@code <version>.zip}.
     *
     * @param version JDK version
     * @return path to the ZIP file, or null if not found
     */
    private Path findVersionZip(String version) {
        Path jdkDir = outputDirectory.resolve("jdk");
        if (!Files.isDirectory(jdkDir)) {
            return null;
        }
        try (var stream = Files.walk(jdkDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(version + ".zip"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to list JDK directory {}: {}", jdkDir, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the path for a specific version's documentation ZIP, or null if not found.
     * Searches recursively under the configured data directory.
     *
     * @param version JDK version
     * @return path to the version ZIP file, or null
     */
    public Path getVersionZip(String version) {
        return findVersionZip(version);
    }

    /**
     * Checks if documentation has been generated for the specified version.
     * Searches recursively under the configured data directory.
     *
     * @param version JDK version
     * @return true if <version>.zip exists and contains index.json
     */
    public boolean isVersionGenerated(String version) {
        Path zipPath = findVersionZip(version);
        if (zipPath == null) {
            return false;
        }
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            return ZIPHelper.findZipEntry(zf, "index.json") != null;
        } catch (IOException e) {
            log.warn("Failed to read ZIP {}: {}", zipPath, e.getMessage());
            return false;
        }
    }
}
