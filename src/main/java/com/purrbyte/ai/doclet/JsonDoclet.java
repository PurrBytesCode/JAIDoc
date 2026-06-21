package com.purrbyte.ai.doclet;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Doclet that serializes the complete Javadoc to JSON.
 *
 * <p>Compatibility (see README):
 * <ul>
 *   <li>Uses the modern {@code jdk.javadoc.doclet} API (JDK 9+). The old
 *       {@code com.sun.javadoc} API was removed in JDK 13.</li>
 *   <li>Jackson 3 ({@code tools.jackson.*}) requires Java 17, so the
 *       application must run with JDK 17 or higher (17, 21, 25, 27...).</li>
 *   <li>To document source code from Java 8 to 27, it is enough that the
 *       source code is compiled with {@code --release N} (or {@code -source N}).</li>
 * </ul>
 *
 * <p>Output (in the directory specified with {@code -d}):
 * <ul>
 *   <li>{@code api/<package>/<Type>.json} — one JSON per top-level type
 *       (nested types go inside their enclosing type's file).</li>
 *   <li>{@code api/<package>/package-info.json} — package documentation.</li>
 *   <li>{@code module-<name>.json} — documentation for each module.</li>
 *   <li>{@code index.json} — manifest with all types/packages/modules.</li>
 *   <li>{@code chunks.jsonl} — one "chunk" per documented element (JSON line with
 *       flat id, text, and metadata), ready to embed.</li>
 * </ul>
 */
@Slf4j
public class JsonDoclet implements Doclet {

    private Reporter reporter;

    private Path outputDir = Path.of("json-doclet-out");
    private String docVersion = null;        // optional documented JDK/artifact version
    private boolean pretty = false;
    private boolean noChunks = false;
    private boolean onlyDocumented = false;
    private Path chunksFile = null;          // default: <out>/chunks.jsonl
    private int maxChunkChars = 4000;
    private int chunkOverlap = 200;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "JsonDoclet";
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        // Supports the latest JDK version it runs on (21, 25, 27...).
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        Set<Option> opts = new TreeSet<>(Comparator.comparing(o -> o.getNames().getFirst()));

        opts.add(new SimpleOption(List.of("-d", "--output-directory"), 1, "Output directory (default: json-doclet-out)") {
            @Override
            public boolean process(String opt, List<String> args) {
                outputDir = Path.of(args.getFirst());
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--doc-version"), 1, "Version recorded in index.json (e.g. the documented JDK version)") {
            @Override
            public boolean process(String opt, List<String> args) {
                docVersion = args.getFirst();
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--pretty"), 0, "Format JSON with indentation") {
            @Override
            public boolean process(String opt, List<String> args) {
                pretty = true;
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--no-chunks"), 0, "Do not generate chunks.jsonl") {
            @Override
            public boolean process(String opt, List<String> args) {
                noChunks = true;
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--chunks-file"), 1, "Path of the JSONL chunks file (default: <out>/chunks.jsonl)") {
            @Override
            public boolean process(String opt, List<String> args) {
                chunksFile = Path.of(args.getFirst());
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--max-chunk-chars"), 1, "Maximum size of a chunk in characters before splitting it (default 4000)") {
            @Override
            public boolean process(String opt, List<String> args) {
                maxChunkChars = Integer.parseInt(args.getFirst());
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--chunk-overlap"), 1, "Overlap between fragments when a chunk is split (default 200)") {
            @Override
            public boolean process(String opt, List<String> args) {
                chunkOverlap = Integer.parseInt(args.getFirst());
                return true;
            }
        });
        opts.add(new SimpleOption(List.of("--only-documented"), 0, "Emit chunks only for elements with a Javadoc comment") {
            @Override
            public boolean process(String opt, List<String> args) {
                onlyDocumented = true;
                return true;
            }
        });
        // Standard doclet options that tools like maven-javadoc-plugin
        // usually pass; accepted and ignored to avoid breaking execution.
        for (String ignored1arg : new String[]{"-doctitle", "-windowtitle", "-charset", "-docencoding", "-bottom", "-link", "-header", "-footer"}) {
            opts.add(new SimpleOption(List.of(ignored1arg), 1, "(ignored)") {
                @Override
                public boolean process(String opt, List<String> args) {
                    return true;
                }
            });
        }
        opts.add(new SimpleOption(List.of("-notimestamp"), 0, "(ignored)") {
            @Override
            public boolean process(String opt, List<String> args) {
                return true;
            }
        });
        return opts;
    }

    @Override
    public boolean run(DocletEnvironment env) {
        try {
            Files.createDirectories(outputDir);
            JsonMapper mapper = JsonMapper.builder()
                    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
            ObjectWriter writer = pretty ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer();
            Path chunksPath = chunksFile != null ? chunksFile : outputDir.resolve("chunks.jsonl");
            try (ChunkWriter chunks = noChunks ? null : new ChunkWriter(chunksPath, mapper, maxChunkChars, chunkOverlap, onlyDocumented)) {
                TypeJsonBuilder builder = new TypeJsonBuilder(env, mapper, chunks);
                ObjectNode index = mapper.createObjectNode();
                index.put("generator", "json-doclet 1.0.0");
                if (docVersion != null) {
                    index.put("version", docVersion);
                }
                index.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
                index.put("javaRuntime", Runtime.version().toString());
                ArrayNode idxModules = index.putArray("modules");
                ArrayNode idxPackages = index.putArray("packages");
                ArrayNode idxTypes = index.putArray("types");
                Set<? extends Element> included = env.getIncludedElements();
                // ---- Modules ----
                for (ModuleElement mod : ElementFilter.modulesIn(included)) {
                    if (mod.isUnnamed()) continue;
                    ObjectNode m = builder.buildModule(mod);
                    String fileName = "module-" + mod.getQualifiedName() + ".json";
                    writeJson(writer, outputDir.resolve(fileName), m);
                    ObjectNode e = idxModules.addObject();
                    e.put("name", mod.getQualifiedName().toString());
                    e.put("file", fileName);
                }
                // ---- Packages ----
                for (PackageElement pkg : ElementFilter.packagesIn(included)) {
                    ObjectNode p = builder.buildPackage(pkg);
                    Path dir = packageDir(pkg);
                    Files.createDirectories(dir);
                    Path file = dir.resolve("package-info.json");
                    writeJson(writer, file, p);
                    ObjectNode e = idxPackages.addObject();
                    e.put("name", pkg.getQualifiedName().toString());
                    e.put("file", outputDir.relativize(file).toString().replace(File.separatorChar, '/'));
                }
                // ---- Tipos de nivel superior (los anidados se serializan dentro) ----
                List<TypeElement> topLevel = new ArrayList<>();
                for (TypeElement t : ElementFilter.typesIn(included)) {
                    if (t.getEnclosingElement() == null || t.getEnclosingElement().getKind() == ElementKind.PACKAGE) {
                        topLevel.add(t);
                    }
                }
                topLevel.sort(Comparator.comparing(t -> t.getQualifiedName().toString()));
                int typeCount = 0;
                for (TypeElement t : topLevel) {
                    ObjectNode json = builder.buildType(t);
                    PackageElement pkg = env.getElementUtils().getPackageOf(t);
                    Path dir = packageDir(pkg);
                    Files.createDirectories(dir);
                    Path file = dir.resolve(t.getSimpleName() + ".json");
                    writeJson(writer, file, json);
                    typeCount++;

                    ObjectNode e = idxTypes.addObject();
                    e.put("qualifiedName", t.getQualifiedName().toString());
                    e.put("kind", t.getKind().name());
                    e.put("package", pkg.getQualifiedName().toString());
                    e.put("file", outputDir.relativize(file).toString().replace(File.separatorChar, '/'));
                }
                index.put("typeCount", typeCount);
                if (chunks != null) {
                    index.put("chunksFile", outputDir.toAbsolutePath().normalize().relativize(chunksPath.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/'));
                    index.put("chunkCount", chunks.count());
                }
                writeJson(mapper.writerWithDefaultPrettyPrinter(), outputDir.resolve("index.json"), index);

                reporter.print(Diagnostic.Kind.NOTE, "JsonDoclet: " + typeCount + " types written to " + outputDir.toAbsolutePath() + (chunks != null ? " (" + chunks.count() + " chunks in " + chunksPath + ")" : ""));
            }
            return true;
        } catch (Exception ex) {
            reporter.print(Diagnostic.Kind.ERROR, "JsonDoclet failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            log.error("JsonDoclet failed", ex);
            return false;
        }
    }

    private Path packageDir(PackageElement pkg) {
        Path dir = outputDir.resolve("api");
        if (!pkg.isUnnamed()) {
            for (String part : pkg.getQualifiedName().toString().split("\\.")) {
                dir = dir.resolve(part);
            }
        }
        return dir;
    }

    private static void writeJson(ObjectWriter writer, Path file, ObjectNode node) {
        // In Jackson 3 exceptions are unchecked (JacksonException).
        writer.writeValue(file.toFile(), node);
    }

    /**
     * Base implementation of {@link Option}.
     */
    private abstract static class SimpleOption implements Option {

        private final List<String> names;
        private final int argCount;
        private final String description;

        SimpleOption(List<String> names, int argCount, String description) {
            this.names = names;
            this.argCount = argCount;
            this.description = description;
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return names;
        }

        @Override
        public String getParameters() {
            return argCount > 0 ? "<valor>" : "";
        }
    }
}
