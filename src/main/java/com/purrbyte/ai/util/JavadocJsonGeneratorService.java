package com.purrbyte.ai.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for generating Javadoc documentation in JSON format from JDK source code.
 * Uses the RaidAndFade/javadoc-json-doclet to produce structured JSON documentation.
 * <p>
 * The service follows the same patterns as {@link JdkSourceDownloaderService}:
 * configurable timeout, progress listener support, and comprehensive error handling.
 */
@Slf4j
@Service
public class JavadocJsonGeneratorService {

    private static final String DEFAULT_DOCLET_CLASS = "com.github.raidandfade.javadoc.json.JavadocDoclet";
    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    private static final String JSON_OUTPUT_PROPERTY = "output";

    /**
     * Listener for generation progress updates.
     */
    public interface ProgressListener {
        void onProgress(String stage, long current, long total);
        void onComplete(Path outputDir);
        void onError(Throwable error);
    }

    private final int timeoutSeconds;
    private final ProgressListener progressListener;

    /**
     * Create a generator with default settings.
     */
    public JavadocJsonGeneratorService() {
        this(DEFAULT_TIMEOUT_SECONDS, null);
    }

    /**
     * Create a generator with custom settings.
     *
     * @param timeoutSeconds   maximum time in seconds for generation
     * @param progressListener optional progress callback
     */
    public JavadocJsonGeneratorService(int timeoutSeconds, ProgressListener progressListener) {
        this.timeoutSeconds = timeoutSeconds;
        this.progressListener = progressListener;
    }

    /**
     * Generate JSON documentation from extracted JDK source code.
     * <p>
     * This is the main entry point that orchestrates the full generation flow:
     * validation, argument building, execution, and verification.
     *
     * @param version     JDK version string (e.g., "25.0.3")
     * @param sourceDir   path to the extracted JDK source directory
     * @param outputDir   directory where JSON files will be written
     * @return path to the generated JSON output directory
     * @throws IOException              if validation fails or javadoc execution errors occur
     * @throws InterruptedException     if the generation is interrupted
     */
    public Path generate(String version, Path sourceDir, Path outputDir)
            throws IOException, InterruptedException {

        log.info("Starting Javadoc JSON generation for JDK v{} from {}", version, sourceDir);

        // Validate inputs
        validateInputs(sourceDir, outputDir);

        // Resolve JDK home for classpath configuration
        Path jdkHome = resolveJdkHome(version);
        if (jdkHome != null) {
            log.debug("Resolved JDK home: {}", jdkHome);
        } else {
            log.warn("Could not resolve JDK home, using default runtime classpath");
        }

        // Report progress
        reportProgress("Building arguments", 0, 3);

        // Build javadoc arguments
        String[] args = buildJavadocArgs(sourceDir, outputDir, version);
        log.debug("Javadoc args: {}", Arrays.toString(args));

        reportProgress("Running javadoc", 1, 3);

        // Execute javadoc
        int exitCode = runJavadoc(args);

        if (exitCode != 0) {
            String errorMsg = "Javadoc generation failed with exit code: " + exitCode;
            log.error(errorMsg);
            if (progressListener != null) {
                progressListener.onError(new IOException(errorMsg));
            }
            throw new IOException(errorMsg);
        }

        reportProgress("Verifying output", 2, 3);

        // Verify output
        verifyOutput(outputDir);

        reportProgress("Complete", 3, 3);

        if (progressListener != null) {
            progressListener.onComplete(outputDir.toAbsolutePath());
        }

        log.info("Javadoc JSON generation complete: {}", outputDir);
        return outputDir.toAbsolutePath();
    }

    /**
     * List all generated JSON files in the output directory.
     *
     * @param outputDir the output directory to scan
     * @return list of paths to generated JSON files
     * @throws IOException if directory cannot be read
     */
    public List<Path> listGeneratedFiles(Path outputDir) throws IOException {
        List<Path> jsonFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outputDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(jsonFiles::add);
        }
        log.debug("Found {} JSON files in {}", jsonFiles.size(), outputDir);
        return jsonFiles;
    }

    /**
     * Validate input and output paths.
     *
     * @param sourceDir the source directory to validate
     * @param outputDir the output directory to validate (may not exist yet)
     * @throws IOException if validation fails
     */
    private void validateInputs(Path sourceDir, Path outputDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            throw new IOException("Source directory does not exist: " + sourceDir);
        }
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Source path is not a directory: " + sourceDir);
        }
        if (!Files.isReadable(sourceDir)) {
            throw new IOException("Source directory is not readable: " + sourceDir);
        }

        // Create output directory if it doesn't exist
        Files.createDirectories(outputDir);
    }

    /**
     * Build javadoc command-line arguments for JSON doclet generation.
     *
     * @param sourceDir the source directory containing JDK sources
     * @param outputDir the directory where JSON output will be written
     * @param version   JDK version string (used for classpath resolution)
     * @return array of javadoc arguments
     */
    private String[] buildJavadocArgs(Path sourceDir, Path outputDir, String version) {
        List<String> argsList = new ArrayList<>();

        // Doclet configuration
        argsList.add("-doclet");
        argsList.add(DEFAULT_DOCLET_CLASS);

        // Output directory for JSON files
        argsList.add("-output");
        argsList.add(outputDir.toAbsolutePath().toString());

        // Source root directory (the directory containing package-source roots)
        argsList.add(sourceDir.toAbsolutePath().toString());

        // Add any additional packages or options if needed
        // For now, we rely on javadoc to discover all source files recursively

        return argsList.toArray(new String[0]);
    }

    /**
     * Execute javadoc compilation via ProcessBuilder.
     * <p>
     * Uses the system javadoc command to ensure compatibility with all JDK versions.
     * The doclet classpath must be configured separately when using custom doclets.
     *
     * @param args javadoc command-line arguments
     * @return exit code (0 = success, non-zero = failure)
     * @throws IOException              if javadoc execution fails unexpectedly
     * @throws InterruptedException     if the thread is interrupted
     */
    private int runJavadoc(String[] args) throws IOException, InterruptedException {
        log.info("Executing javadoc with timeout: {}s", timeoutSeconds);

        // Locate javadoc executable from current JVM
        String javaHome = System.getProperty("java.home");
        Path javadocPath = Path.of(javaHome, "bin", "javadoc");
        
        List<String> command;
        if (Files.exists(javadocPath)) {
            command = Arrays.asList(javadocPath.toAbsolutePath().toString());
        } else {
            // Fallback to system PATH
            command = Collections.singletonList("javadoc");
        }

        // Build full command with args
        List<String> fullCommand = new ArrayList<>(command);
        fullCommand.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Read output for logging
        StringBuilder output = new StringBuilder();
        boolean completed = process.waitFor(timeoutSeconds * 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new IOException("Javadoc execution timed out after " + timeoutSeconds + " seconds");
        }

        // Read stdout/stderr
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (line.contains("error:")) {
                    log.error("Javadoc error: {}", line);
                } else if (!line.trim().isEmpty()) {
                    log.debug("Javadoc: {}", line);
                }
            }
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("Javadoc failed with exit code {}: {}", exitCode, output.toString());
        } else {
            log.info("Javadoc completed successfully");
        }

        return exitCode;
    }

    /**
     * Resolve the JDK home directory for a given version.
     * <p>
     * Attempts to find a specific JDK installation:
     * 1. Check environment variables (JAVA_HOME_XX)
     * 2. Check system JAVA_HOME
     * 3. Return null to use default runtime classpath
     *
     * @param version JDK version string (e.g., "25.0.3")
     * @return path to JDK home, or null to use default
     */
    private Path resolveJdkHome(String version) {
        // Try JAVA_HOME_<version> pattern (similar to jenv)
        String versionSpecificEnv = "JAVA_HOME_" + version.replace('.', '_').toUpperCase();
        String envValue = System.getenv(versionSpecificEnv);
        if (envValue != null && !envValue.isEmpty()) {
            Path path = Path.of(envValue);
            if (Files.exists(path)) {
                return path;
            }
        }

        // Fallback to system JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            return Path.of(javaHome);
        }

        // Use runtime Java home as last resort
        String runtimeHome = System.getProperty("java.home");
        if (runtimeHome != null) {
            return Path.of(runtimeHome).getParent();
        }

        return null;
    }

    /**
     * Verify that JSON output files were generated successfully.
     *
     * @param outputDir the output directory to verify
     * @throws IOException if verification fails
     */
    private void verifyOutput(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            throw new IOException("Output directory does not exist after generation: " + outputDir);
        }

        List<Path> jsonFiles = listGeneratedFiles(outputDir);
        if (jsonFiles.isEmpty()) {
            log.warn("No JSON files generated in {}", outputDir);
        } else {
            log.info("Verified {} JSON files generated", jsonFiles.size());
        }
    }

    /**
     * Report progress through the listener if available.
     */
    private void reportProgress(String stage, long current, long total) {
        if (progressListener != null) {
            progressListener.onProgress(stage, current, total);
        }
    }
}
