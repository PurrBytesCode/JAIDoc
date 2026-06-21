package com.purrbyte.ai.service;

import com.purrbyte.ai.domain.JdkDocChunk;
import com.purrbyte.ai.domain.JdkDocElement;
import com.purrbyte.ai.domain.JdkVersion;
import com.purrbyte.ai.model.ElementKind;
import com.purrbyte.ai.model.IngestStatus;
import com.purrbyte.ai.repository.JdkDocChunkRepository;
import com.purrbyte.ai.repository.JdkDocElementRepository;
import com.purrbyte.ai.repository.JdkVersionRepository;
import com.purrbyte.ai.util.JdkDistributionDownloader;
import com.purrbyte.ai.util.ZIPHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final int BATCH_SIZE = 200;

    private final DocumentationService documentationService;
    private final EmbeddingService embeddingService;
    private final JdkVersionRepository jdkVersionRepository;
    private final JdkDocElementRepository jdkDocElementRepository;
    private final JdkDocChunkRepository jdkDocChunkRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public JdkVersion ingest(String version) throws IOException {
        long totalStart = System.currentTimeMillis();
        log.info("Starting ingest for JDK {} (ZIP: {})", version, documentationService.getVersionZip(version));
        Path zipPath = documentationService.getVersionZip(version);
        if (zipPath == null) {
            throw new IOException("No generated documentation found for version " + version);
        }
        Optional<JdkVersion> optionalJdkVersion = jdkVersionRepository.findByVersion(version);
        JdkVersion jdkVersion = optionalJdkVersion.orElse(null);
        if (jdkVersion != null) {
            log.info("Version {} already exists, returning existing version (status={})", version, jdkVersion.getStatus());
            return jdkVersion;
        }
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            long t0 = System.currentTimeMillis();
            log.info("[{}/manifest] Reading manifest from ZIP", version);
            jdkVersion = readManifestFromZip(version, zipFile);
            jdkVersion.setStatus(IngestStatus.READY);
            jdkVersion.setIngestedAt(LocalDateTime.now());
            jdkVersionRepository.save(jdkVersion);
            log.info("[{}/manifest] Manifest loaded — {} modules, {} packages, {} types, {} chunks ({}ms)",
                    version, jdkVersion.getModuleCount(), jdkVersion.getPackageCount(),
                    jdkVersion.getTypeCount(), jdkVersion.getChunkCount(), elapsedMs(t0));
            try {
                log.info("[{}/elements] Ingesting structural elements from ZIP", version);
                long startTime = System.currentTimeMillis();
                ingestElementsFromZip(jdkVersion, zipFile, jdkVersion.getTypeCount() + jdkVersion.getPackageCount() + jdkVersion.getModuleCount(), t0);
                log.info("[{}/elements] Structural elements ingested ({}ms)", version, elapsedMs(startTime));
                log.info("[{}/chunks] Ingesting {} chunks and generating embeddings (ZIP: {})", version, jdkVersion.getChunkCount(), documentationService.getVersionZip(version));
                long endTime = System.currentTimeMillis();
                ingestChunksFromZip(jdkVersion, zipFile, jdkVersion.getChunkCount(), startTime);
                log.info("Ingested JDK {}: {} chunks ({}ms)", version, jdkVersion.getChunkCount(), elapsedMs(endTime));
            } catch (RuntimeException | IOException e) {
                jdkVersion.setStatus(IngestStatus.FAILED);
                log.error("Ingestion failed for {}: {} ({})", version, e.getMessage(), elapsedMs(totalStart), e);
                throw e;
            }
            log.info("Total ingest for JDK {}: {} ({}ms)", version, formatDuration(totalStart), elapsedMs(totalStart));
        }
        return jdkVersion;
    }


    private JdkVersion readManifestFromZip(String version, ZipFile zipFile) throws IOException {
        ZipEntry indexEntry = ZIPHelper.findZipEntry(zipFile, "index.json");
        if (indexEntry == null) {
            throw new IOException("index.json not found in ZIP for version " + version);
        }
        byte[] bytes = zipFile.getInputStream(indexEntry).readAllBytes();
        JsonNode root = jsonMapper.readTree(bytes);
        int[] parsed = JdkDistributionDownloader.parseVersion(version); // {major, minor, security}
        String generatedAt = str(root, "generatedAt");
        Instant instantParsed = (generatedAt != null) ? Instant.parse(generatedAt) : null;
        return JdkVersion.builder()
                .version(version)
                .major(parsed[0])
                .minor(parsed[1])
                .security(parsed[2])
                .javaRuntime(str(root, "javaRuntime"))
                .generator(str(root, "generator"))
                .generatedAt((instantParsed != null) ? LocalDateTime.ofInstant(instantParsed, ZoneId.systemDefault()) : null)
                .typeCount(root.path("typeCount").asInt())
                .chunkCount(root.path("chunkCount").asInt())
                .packageCount(root.path("packages").size())
                .moduleCount(root.path("modules").size())
                .build();
    }

    private void ingestElementsFromZip(JdkVersion jdkVersion, ZipFile zipFile, int totalExpected, long startMillis) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        int total = 0;
        int persisted = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".json") && !name.equals("index.json")) {
                total++;
                byte[] bytes = zipFile.getInputStream(entry).readAllBytes();
                String json = new String(bytes, StandardCharsets.UTF_8);
                if (persistElementFromJson(jdkVersion, json)) {
                    persisted++;
                }
                if (total % BATCH_SIZE == 0) {
                    log.info("[{}/elements] Processed {} / {} entries, persisted {} elements ({}ms elapsed)", jdkVersion.getVersion(), total, totalExpected, persisted, elapsedMs(startMillis));
                }
            }
        }
        if (total > BATCH_SIZE && total % (BATCH_SIZE * 10) == 0) {
            log.info("[{}/elements] Processed {} / {} entries, persisted {} elements ({}ms elapsed)", jdkVersion.getVersion(), total, totalExpected, persisted, elapsedMs(startMillis));
        }
    }

    /**
     * Returns true if the element was persisted, false if it was skipped (e.g., missing 'kind' field).
     */
    private boolean persistElementFromJson(JdkVersion jdkVersion, String json) {
        JsonNode node = jsonMapper.readTree(json);
        String kind = node.path("kind").asString(null);
        if (kind == null) {
            log.debug("Skipping element without 'kind' field: {}", json);
            return false;
        }
        String name = str(node, "name");
        String qualifiedId = switch (kind) {
            case "MODULE" -> "module:" + name;
            case "PACKAGE" -> name;
            default -> str(node, "qualifiedName");
        };
        JdkDocElement jdkDocElement = JdkDocElement.builder()
                .jdkVersion(jdkVersion)
                .rawJson(json)
                .kind("MODULE".equals(kind) || "PACKAGE".equals(kind) ? ElementKind.valueOf(kind) : ElementKind.TYPE)
                .qualifiedId(qualifiedId)
                .simpleName((kind.equals("TYPE")) ? name : null)
                .packageName((kind.equals("TYPE")) ? str(node, "package") : (kind.equals("PACKAGE") ? name : null))
                .moduleName((kind.equals("MODULE")) ? name : (kind.equals("TYPE") ? str(node, "module") : null))
                .build();
        jdkDocElementRepository.save(jdkDocElement);
        return true;
    }

    private void ingestChunksFromZip(JdkVersion jdkVersion, ZipFile zipFile, int totalChunks, long startMillis) throws IOException {
        ZipEntry chunksEntry = ZIPHelper.findZipEntry(zipFile, "chunks.jsonl");
        if (chunksEntry == null) {
            log.info("[{}/chunks] No chunks.jsonl found in ZIP", jdkVersion.getVersion());
            return;
        }
        int count = 0;
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(chunksEntry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = jsonMapper.readTree(line);
                JsonNode meta = node.path("metadata");
                String chunkId = node.path("id").asString(null);
                String text = node.path("text").asString(null);
                String ownerId = str(meta, "type");
                if (ownerId == null) {
                    ownerId = chunkId;
                }
                JdkDocChunk jdkDocChunk = JdkDocChunk.builder()
                        .jdkVersion(jdkVersion)
                        .version(jdkVersion.getVersion())
                        .chunkId(chunkId)
                        .text(text)
                        .embedding(embeddingService.embedPassage(text))
                        .kind(str(meta, "kind"))
                        .qualifiedType(str(meta, "type"))
                        .packageName(str(meta, "package"))
                        .moduleName(str(meta, "module"))
                        .member(str(meta, "member"))
                        .signature(str(meta, "signature"))
                        .since(str(meta, "since"))
                        .deprecated(meta.path("deprecated").asBoolean())
                        .sourceFile(str(meta, "file"))
                        .sourceLine(meta.path("line").asInt())
                        .part(meta.path("part").asInt())
                        .parts(meta.path("parts").asInt())
                        .parentChunkId(str(meta, "parentId"))
                        .build();
                jdkDocElementRepository.findByJdkVersionAndQualifiedId(jdkVersion, ownerId).ifPresent(jdkDocChunk::setJDKDocElement);
                jdkDocChunkRepository.save(jdkDocChunk);
                if (++count % BATCH_SIZE == 0) {
                    jdkDocChunkRepository.flush();
                }
                if (count > BATCH_SIZE && count % (BATCH_SIZE * 10) == 0) {
                    log.info("[{}/chunks] Processed {} / {} chunks ({} skipped, {}ms elapsed)", jdkVersion.getVersion(), count, totalChunks, skipped, elapsedMs(startMillis));
                }
            }
        }
        log.info("[{}/chunks] Chunk ingestion complete: {} / {} processed, {} skipped ({}ms elapsed)", jdkVersion.getVersion(), count, totalChunks, skipped, elapsedMs(startMillis));
    }

    /**
     * Returns the field's text, or {@code null} when absent/null (Jackson 3 tree access).
     */
    private static String str(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString(null);
    }

    /**
     * Formats elapsed time as a human-readable string.
     */
    private static String formatDuration(long startMillis) {
        long elapsedMs = elapsedMs(startMillis);
        if (elapsedMs < 1000) {
            return elapsedMs + "ms";
        }
        long seconds = elapsedMs / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m" + secs + "s";
    }

    private static long elapsedMs(long startMillis) {
        return System.currentTimeMillis() - startMillis;
    }
}
