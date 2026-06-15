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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class DocumentationService {

    private static final long JAVADOC_TIMEOUT_SECONDS = 600;

    private final JdkSourceDownloader jdkSourceDownloader;
    private final Path workDirectory;
    private final Path outputDirectory;

    public DocumentationService(
            JdkSourceDownloader jdkSourceDownloader,
            @Value("${doclet.work.directory}") Path workDirectory,
            @Value("${doclet.output.directory}") Path outputDirectory) {
        this.jdkSourceDownloader = jdkSourceDownloader;
        this.workDirectory = workDirectory;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Generates JDK documentation for the specified version using the JsonDoclet.
     *
     * <p>The pipeline is:
     * <ol>
     *   <li>Download JDK source zip — 0 to 40%</li>
     *   <li>Extract source zip — 40 to 55%</li>
     *   <li>Run javadoc with JsonDoclet and copy to data/ — 55 to 100%</li>
     * </ol>
     *
     * @param version          JDK version (e.g. "25.0.3", "21.0.11")
     * @param progressCallback callback for progress updates (each phase reports its own 0-100%)
     * @return future with the path to the generated documentation directory in data/
     */
    public CompletableFuture<Path> generateJdkDocumentation(String version, Consumer<Progress> progressCallback) {
        return jdkSourceDownloader.downloadSource(version, p -> {
                    if (progressCallback != null) progressCallback.accept(p);
                })
                .thenApply(zippedPath -> {
                    try {
                        return extractSourceZip(zippedPath, version,
                                p -> {
                                    if (progressCallback != null) {
                                        progressCallback.accept(new Progress(p, Progress.MODULE_EXTRACT));
                                    }
                                });
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
                .thenApply(sourceRoot -> {
                    try {
                        return runJavadocDoclet(sourceRoot, version,
                                p -> {
                                    if (progressCallback != null) {
                                        progressCallback.accept(new Progress(p.percentage(), Progress.MODULE_JAVADOC));
                                    }
                                });
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
                .exceptionally(ex -> {
                    log.error("JDK documentation generation failed for version {}: {}", version, ex.getMessage());
                    throw new CompletionException(ex);
                });
    }

    private Path extractSourceZip(Path zipFile, String version, Consumer<Double> progressCallback) throws IOException {
        Path extractDir = workDirectory.resolve("jdk-sources").resolve(version);
        if (Files.exists(extractDir)) {
            Path existingRoot = findSourceRoot(extractDir);
            log.info("Source already extracted at {}", existingRoot);
            return existingRoot;
        }
        Files.createDirectories(extractDir);
        int totalEntries;
        try (ZipFile zipFileObj = new ZipFile(zipFile.toFile())) {
            long count = zipFileObj.stream().filter(e -> !e.isDirectory()).count();
            totalEntries = (int) count;
        }
        int processed = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path dest = extractDir.resolve(entry.getName()).normalize();
                    if (!dest.startsWith(extractDir)) {
                        log.warn("Skipping zip-slip entry: {}", entry.getName());
                        zis.closeEntry();
                        continue;
                    }
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    processed++;
                    if (progressCallback != null) {
                        progressCallback.accept(processed / (double) totalEntries * 100.0);
                    }
                } else {
                    Files.createDirectories(extractDir.resolve(entry.getName()).normalize());
                }
                zis.closeEntry();
            }
        }
        Path sourceRoot = findSourceRoot(extractDir);
        log.info("JDK source extracted to: {}", sourceRoot);
        return sourceRoot;
    }

    private Path findSourceRoot(Path extractDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extractDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) return entry;
            }
        }
        return extractDir;
    }

    private Path runJavadocDoclet(Path sourceRoot, String version, Consumer<Progress> progressCallback) throws IOException {
        Path tempOutputDir = workDirectory.resolve("javadoc-out").resolve(version);
        Files.createDirectories(tempOutputDir);
        String osName = System.getProperty("os.name").toLowerCase();
        Path javadocBin = Path.of(System.getProperty("java.home"), "bin", osName.contains("win") ? "javadoc.exe" : "javadoc");
        if (!Files.exists(javadocBin)) {
            throw new IllegalStateException("javadoc binary not found at: " + javadocBin);
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
        command.add("-sourcepath");
        Path srcDir = sourceRoot.resolve("src");
        if (!Files.exists(srcDir)) {
            throw new IllegalStateException("Source directory not found: " + srcDir);
        }
        command.add(srcDir.toString());
        command.add("-source");
        command.add(String.valueOf(JdkSourceDownloader.extractMajorVersion(version)));
        command.add("-d");
        command.add(tempOutputDir.toString());
        command.add("--pretty");
        command.add("-subpackages");
        command.add("java");
        command.add("jdk");
        command.add("com.sun");
        command.add("javax");
        command.add("org.ietf");
        command.add("org.w3c");
        command.add("org.xml.sax");
        log.info("Executing javadoc: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        CompletableFuture<Void> progressTicker = startProgressTicker(p -> {
                    if (progressCallback != null) {
                        progressCallback.accept(new Progress(p, Progress.MODULE_JAVADOC));
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
        if (exitCode != 0) {
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc exited with code " + exitCode);
        }
        if (!Files.exists(tempOutputDir.resolve("index.json"))) {
            deleteDirectory(tempOutputDir);
            throw new IOException("javadoc completed but index.json was not created");
        }
        Path dataDir = outputDirectory.resolve(version);
        Files.createDirectories(dataDir);
        try {
            copyDirectory(tempOutputDir, dataDir);
        } catch (IOException e) {
            throw new IOException("Failed to copy javadoc output to data/: " + e.getMessage(), e);
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
     * Resolves the path to the doclet JAR in the doclet/ directory.
     *
     * <p>Looks for the JAR {@code JAIDoc-doclet.jar} in the project's doclet/ directory.
     * Returns null if not found — javadoc will then use its own classpath.
     */
    String resolveDocletPath() {
        String projDir = System.getProperty("user.dir");
        Path docletDir = Path.of(projDir, "doclet");
        try (var stream = Files.list(docletDir)) {
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
