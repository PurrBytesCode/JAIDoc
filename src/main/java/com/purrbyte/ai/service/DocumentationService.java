package com.purrbyte.ai.service;

import com.purrbyte.ai.util.JdkSourceDownloader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.function.Consumer;

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
     *   <li>Download JDK source zip (40%)</li>
     *   <li>Extract source zip (15%)</li>
     *   <li>Run javadoc with JsonDoclet and copy to data/ (45%)</li>
     * </ol>
     *
     * @param version JDK version (e.g. "25.0.3", "21.0.11")
     * @param progressCallback callback for progress updates (0.0 to 1.0)
     * @return future with the path to the generated documentation directory in data/
     */
    public CompletableFuture<Path> generateJdkDocumentation(String version, Consumer<Double> progressCallback) {
        final double DOWNLOAD_WEIGHT = 0.40;
        final double EXTRACT_WEIGHT = 0.15;
        final double JAVADOC_WEIGHT = 0.45;
        Consumer<Double> downloadProgress = p -> {
            if (progressCallback != null) progressCallback.accept(p * DOWNLOAD_WEIGHT);
        };
        return jdkSourceDownloader.downloadSource(version, downloadProgress)
                .thenApply(zippedPath -> {
                    try {
                        return extractSourceZip(zippedPath, version,
                                p -> progressCallback.accept(DOWNLOAD_WEIGHT + p * EXTRACT_WEIGHT));
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
                .thenApply(sourceRoot -> {
                    try {
                        return runJavadocDoclet(sourceRoot, version,
                                p -> progressCallback.accept(DOWNLOAD_WEIGHT + EXTRACT_WEIGHT + p * JAVADOC_WEIGHT));
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
                .exceptionally(ex -> {
                    log.error("JDK documentation generation failed for version {}: {}", version, ex.getMessage());
                    throw new CompletionException(ex);
                });
    }

    /**
     * Extracts a JDK source zip to the work directory.
     *
     * @param zipFile path to the downloaded zip
     * @param version JDK version
     * @param progressCallback progress callback (0.0 to 1.0 for this phase)
     * @return path to the extracted source root directory
     */
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

    /**
     * Finds the top-level directory in the extracted zip.
     * The zip contains a single top-level directory (e.g. "jdk-25.0.3-ga/").
     */
    private Path findSourceRoot(Path extractDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extractDir)) {
            Iterator<Path> it = stream.iterator();
            if (it.hasNext()) {
                Path entry = it.next();
                if (Files.isDirectory(entry)) return entry;
            }
        }
        return extractDir;
    }

    /**
     * Runs javadoc with the JsonDoclet to generate JSON documentation.
     *
     * <p>Output goes to a temp directory first, then is copied to the persistent data/ directory.
     *
     * @param sourceRoot path to the extracted JDK source root
     * @param version JDK version
     * @param progressCallback progress callback (0.0 to 1.0 for this phase)
     * @return path to the generated documentation directory in data/
     */
    private Path runJavadocDoclet(Path sourceRoot, String version, Consumer<Double> progressCallback) throws IOException {
        Path tempOutputDir = workDirectory.resolve("javadoc-out").resolve(version);
        Files.createDirectories(tempOutputDir);
        String javaHome = System.getProperty("java.home");
        String osName = System.getProperty("os.name").toLowerCase();
        Path javadocBin = Path.of(javaHome, "bin", osName.contains("win") ? "javadoc.exe" : "javadoc");
        if (!Files.exists(javadocBin)) {
            throw new IllegalStateException("javadoc binary not found at: " + javadocBin);
        }
        String fatJarPath = resolveFatJarPath();
        List<String> command = new ArrayList<>();
        command.add(javadocBin.toString());
        if (fatJarPath != null) {
            command.add("-docletpath");
            command.add(fatJarPath);
        }
        command.add("-doclet");
        command.add("com.purrbyte.ai.doclet.JsonDoclet");
        command.add("-sourcepath");
        command.add(sourceRoot.toString());
        command.add("-source");
        command.add(String.valueOf(JdkSourceDownloader.extractMajorVersion(version)));
        command.add("-d");
        command.add(tempOutputDir.toString());
        command.add("--pretty");
        command.add("-subpackages");
        command.add("java;jdk;com.sun;javax;org.ietf;org.w3c;org.xml.sax");
        log.info("Executing javadoc: {}", command);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        CompletableFuture<Void> progressTicker = startProgressTicker(progressCallback);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[javadoc] {}", line);
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
            cleanupOutput(tempOutputDir);
            throw new IOException("javadoc process interrupted", e);
        }
        progressTicker.cancel(true);
        if (!exited) {
            process.destroyForcibly();
            cleanupOutput(tempOutputDir);
            throw new IOException("javadoc process timed out after " + JAVADOC_TIMEOUT_SECONDS + " seconds");
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            cleanupOutput(tempOutputDir);
            throw new IOException("javadoc exited with code " + exitCode);
        }
        if (!Files.exists(tempOutputDir.resolve("index.json"))) {
            cleanupOutput(tempOutputDir);
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
     * Resolves the path to the Spring Boot fat JAR at runtime.
     *
     * <p>Three strategies, tried in order:
     * <ol>
     *   <li>ProtectionDomain — in production (java -jar), this returns the fat JAR URI</li>
     *   <li>Classpath scan — in dev/IDE mode, look for JAIDoc-*.jar</li>
     *   <li>Fallback: null — if no JAR found, javadoc will use its own classpath</li>
     * </ol>
     */
    String resolveFatJarPath() {
        try {
            java.net.URL location = DocumentationService.class.getProtectionDomain().getCodeSource().getLocation();
            java.net.URI locationUri = location.toURI();
            Path jarPath = Path.of(locationUri);
            if (Files.exists(jarPath) && Files.isRegularFile(jarPath)) {
                // Validate it's a Spring Boot fat JAR by checking for BOOT-INF/classes entry
                if (isFatJar(jarPath)) {
                    log.debug("Fat JAR resolved via ProtectionDomain: {}", jarPath);
                    return jarPath.toString();
                }
            }
        } catch (Exception e) {
            log.debug("ProtectionDomain resolution failed: {}", e.getMessage());
        }
        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            String[] entries = classpath.split(File.pathSeparator);
            for (String entry : entries) {
                if (entry.contains("JAIDoc-") && entry.endsWith(".jar")) {
                    Path jarPath = Path.of(entry);
                    if (Files.exists(jarPath)) {
                        log.debug("Fat JAR resolved via classpath scan: {}", jarPath);
                        return jarPath.toString();
                    }
                }
            }
        }
        log.debug("No fat JAR found on classpath; javadoc will use its own classpath");
        return null;
    }

    /**
     * Validates that a JAR file is a Spring Boot fat JAR by checking for the BOOT-INF/classes entry.
     */
    private boolean isFatJar(Path jarPath) {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(jarPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("BOOT-INF/classes/".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read JAR entries from {}: {}", jarPath, e.getMessage());
        }
        return false;
    }

    /**
     * Creates an async progress ticker that increments progress during the javadoc phase.
     */
    private CompletableFuture<Void> startProgressTicker(Consumer<Double> progressCallback) {
        if (progressCallback == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                // Progress from 5% to 95% during javadoc execution
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                Path dest = destination.resolve(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(dest);
                    copyDirectory(entry, dest);
                } else {
                    Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Deletes a directory and all its contents recursively.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteDirectory(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(dir);
    }

    /**
     * Cleans up the output directory on failure (temp dir or partial output).
     */
    private void cleanupOutput(Path outputDir) {
        try {
            deleteDirectory(outputDir);
            log.debug("Cleaned up output directory: {}", outputDir);
        } catch (IOException e) {
            log.warn("Failed to clean up output directory {}: {}", outputDir, e.getMessage());
        }
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
