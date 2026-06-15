package com.purrbyte.ai.service;

import com.purrbyte.ai.model.Progress;
import com.purrbyte.ai.util.JdkSourceDownloader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Generates JSON documentation for the JDK using the {@code JsonDoclet}.
 *
 * <p>The source is the complete {@code lib/src.zip} that ships with the running JDK (the GitHub
 * source tree is incomplete because many classes — e.g. the {@code java.nio} buffers — are generated
 * at build time). Because the archive belongs to the running JDK, this service can only document the
 * JDK version that matches {@code JAVA_HOME}.
 */
@Service
@Slf4j
public class DocumentationService {

    private static final long JAVADOC_TIMEOUT_SECONDS = 600;
    private static final int JAVADOC_MAX_DIAGNOSTICS = 100_000;

    private final Executor documentationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Path workDirectory;
    private final Path outputDirectory;
    private final Path docletDirectory;
    private final Path javadocHome;
    private final List<String> configuredModules;

    public DocumentationService(
            @Value("${doclet.work.directory}") Path workDirectory,
            @Value("${doclet.output.directory}") Path outputDirectory,
            @Value("${doclet.modules:}") String modulesCsv,
            @Value("${doclet.jar.directory:doclet}") Path docletDirectory,
            @Value("${doclet.javadoc.home:}") String javadocHome) {
        this.workDirectory = workDirectory;
        this.outputDirectory = outputDirectory;
        this.docletDirectory = docletDirectory;
        this.javadocHome = (javadocHome == null || javadocHome.isBlank())
                ? Path.of(System.getProperty("java.home"))
                : Path.of(javadocHome);
        this.configuredModules = parseModules(modulesCsv);
    }

    /**
     * Generates JDK documentation for the specified version using the JsonDoclet.
     *
     * <p>The pipeline is:
     * <ol>
     *   <li>Locate the running JDK's {@code lib/src.zip} (must match the requested major version)</li>
     *   <li>Extract the source archive — reports {@link Progress#MODULE_EXTRACT}</li>
     *   <li>Run javadoc with the JsonDoclet in module mode and copy to the output directory —
     *       reports {@link Progress#MODULE_JAVADOC}</li>
     * </ol>
     *
     * @param version          JDK version (e.g. "25.0.3"); its major must match the running JDK
     * @param progressCallback callback for progress updates (each phase reports its own 0-100%)
     * @return future with the path to the generated documentation directory in the output directory
     */
    public CompletableFuture<Path> generateJdkDocumentation(String version, Consumer<Progress> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        Path srcZip = locateSrcZip(version);
                        Path moduleRoot = extractSourceZip(srcZip, version, p -> {
                            if (progressCallback != null) {
                                progressCallback.accept(Progress.of(p, Progress.MODULE_EXTRACT));
                            }
                        });
                        List<String> modules = resolveModules(moduleRoot);
                        log.info("Documenting JDK {} ({} modules)", version, modules.size());
                        return runJavadocDoclet(moduleRoot, version, modules, progressCallback);
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
     * Locates the {@code lib/src.zip} of the running JDK, validating that its major version matches
     * the requested version.
     *
     * @param version requested JDK version (e.g. "25.0.3")
     * @return path to {@code lib/src.zip}
     * @throws IOException if the major version does not match the running JDK or the archive is missing
     */
    private Path locateSrcZip(String version) throws IOException {
        int requestedMajor = JdkSourceDownloader.extractMajorVersion(version);
        int runningMajor = Runtime.version().feature();
        if (requestedMajor != runningMajor) {
            throw new IOException("Cannot document JDK " + version + ": lib/src.zip belongs to the running JDK "
                    + Runtime.version() + " (major " + runningMajor + "). Run with a JDK whose major version is "
                    + requestedMajor + ".");
        }
        Path srcZip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
        if (!Files.exists(srcZip)) {
            throw new IOException("JDK source archive not found at " + srcZip
                    + " — this JDK distribution does not ship lib/src.zip.");
        }
        return srcZip;
    }

    /**
     * Extracts a source zip into {@code <work>/jdk-sources/<version>} with zip-slip protection.
     * The extraction is idempotent: if the directory already exists it is reused.
     *
     * @return the extract directory, which is the {@code --module-source-path} root (its immediate
     * subdirectories are JDK modules)
     */
    private Path extractSourceZip(Path zipFile, String version, Consumer<Double> progressCallback) throws IOException {
        Path extractDir = workDirectory.resolve("jdk-sources").resolve(version);
        if (Files.exists(extractDir)) {
            log.info("Source already extracted at {}", extractDir);
            return extractDir;
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
        return extractDir;
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

    private Path runJavadocDoclet(Path moduleRoot, String version, List<String> modules,
                                  Consumer<Progress> progressCallback) throws IOException {
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
        // diagnostic limits so javadoc still runs the doclet instead of aborting at the default cap.
        command.add("-Xmaxerrs");
        command.add(String.valueOf(JAVADOC_MAX_DIAGNOSTICS));
        command.add("-Xmaxwarns");
        command.add(String.valueOf(JAVADOC_MAX_DIAGNOSTICS));
        log.info("Executing javadoc: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        CompletableFuture<Void> progressTicker = startProgressTicker(p -> {
            if (progressCallback != null) {
                progressCallback.accept(Progress.of(p, Progress.MODULE_JAVADOC));
            }
        });
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[javadoc] {}", line);
            }
        } catch (IOException e) {
            log.warn("Error reading javadoc output: {}", e.getMessage());
        }
        boolean exited;
        try {
            exited = process.waitFor(JAVADOC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            progressTicker.cancel(true);
            Thread.currentThread().interrupt();
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc process interrupted", e);
        }
        progressTicker.cancel(true);
        if (!exited) {
            process.destroyForcibly();
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc process timed out after " + JAVADOC_TIMEOUT_SECONDS + " seconds");
        }
        int exitCode = process.exitValue();
        // The doclet runs only after type attribution; missing generated symbols can leave a non-zero
        // exit code even though valid output was produced. Treat the presence of index.json as success.
        if (!Files.exists(tempOutputDir.resolve("index.json"))) {
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc did not produce index.json (exit code " + exitCode + ")");
        }
        if (exitCode != 0) {
            log.warn("javadoc exited with code {} but index.json was produced; continuing with partial documentation",
                    exitCode);
        }
        Path dataDir = outputDirectory.resolve(version);
        Files.createDirectories(dataDir);
        try {
            copyDirectory(tempOutputDir, dataDir);
        } catch (IOException e) {
            throw new IOException("Failed to copy javadoc output to output directory: " + e.getMessage(), e);
        }
        try {
            deleteDirectory(tempOutputDir);
        } catch (IOException e) {
            log.warn("Failed to clean up temp dir {}: {}", tempOutputDir, e.getMessage());
        }
        log.info("JDK documentation generated at {}", dataDir);
        return dataDir;
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
     * Creates an async progress ticker that increments progress during the javadoc phase.
     *
     * @param progressCallback callback for raw progress percentage (0.0 to 100.0)
     */
    private CompletableFuture<Void> startProgressTicker(Consumer<Double> progressCallback) {
        if (progressCallback == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                double progress = 5.0 + (95.0 * Math.min(i / 100.0, 1.0));
                progressCallback.accept(progress);
                i++;
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    /**
     * Copies all contents from the source directory to the destination directory.
     */
    private void copyDirectory(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(path -> {
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

    /**
     * Deletes a directory and all its contents recursively.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
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
     * Lists available JDK versions that have been generated.
     *
     * @return sorted list of version strings
     */
    public List<String> listAvailableVersions() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDirectory)) {
            List<String> versions = new ArrayList<>();
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && Files.exists(entry.resolve("index.json"))) {
                    versions.add(entry.getFileName().toString());
                }
            }
            Collections.sort(versions);
            return versions;
        } catch (IOException e) {
            log.warn("Failed to list available versions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns the path for a specific version's documentation, or null if not found.
     *
     * @param version JDK version
     * @return path to the version directory, or null
     */
    public Path getVersionDir(String version) {
        Path versionDir = outputDirectory.resolve(version);
        if (Files.exists(versionDir)) {
            return versionDir;
        }
        return null;
    }

    /**
     * Checks if documentation has been generated for the specified version.
     *
     * @param version JDK version
     * @return true if index.json exists for this version
     */
    public boolean isVersionGenerated(String version) {
        return Files.exists(outputDirectory.resolve(version).resolve("index.json"));
    }
}
