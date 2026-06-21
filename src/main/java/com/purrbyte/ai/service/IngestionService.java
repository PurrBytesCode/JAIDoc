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
import jakarta.persistence.EntityManager;
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
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final int BATCH_SIZE = 200;

    private final EntityManager em;
    private final DocumentationService documentationService;
    private final EmbeddingService embeddingService;
    private final JdkVersionRepository versionRepository;
    private final JdkDocElementRepository jdkDocElementRepository;
    private final JdkDocChunkRepository jdkDocChunkRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public JdkVersion ingest(String version) throws IOException {
        long totalStart = System.nanoTime();
        log.info("Starting ingest for JDK {} (ZIP: {})", version, documentationService.getVersionZip(version));
        Path zipPath = documentationService.getVersionZip(version);
        if (zipPath == null) {
            throw new IOException("No generated documentation found for version " + version);
        }
        // Idempotent: drop any prior ingest of this version (cascade removes elements + chunks).
        versionRepository.findByVersion(version).ifPresent(versionRepository::delete);
        // Clear the Hibernate session after the delete to prevent stale actions from corrupting
        // the session state. Without this, the next save() may try to UPDATE instead of INSERT
        // because the session still has unprocessed delete actions.
        versionRepository.flush();
        em.clear();
        JdkVersion jdkVersion;
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            long t0 = System.nanoTime();
            log.info("[{}/manifest] Reading manifest from ZIP", version);
            jdkVersion = readManifestFromZip(version, zipFile);
            versionRepository.save(jdkVersion);
            log.info("[{}/manifest] Manifest loaded — {} modules, {} packages, {} types, {} chunks ({}ms)",
                    version, jdkVersion.getModuleCount(), jdkVersion.getPackageCount(),
                    jdkVersion.getTypeCount(), jdkVersion.getChunkCount(), elapsedMs(t0));
            try {
                log.info("[{}/elements] Ingesting structural elements from ZIP", version);
                long t1 = System.nanoTime();
                ingestElementsFromZip(jdkVersion, zipFile, jdkVersion.getTypeCount() + jdkVersion.getPackageCount() + jdkVersion.getModuleCount(), t0);
                versionRepository.flush();
                log.info("[{}/elements] Structural elements ingested ({}ms)", version, elapsedMs(t1));
                log.info("[{}/chunks] Ingesting {} chunks and generating embeddings (ZIP: {})", version, jdkVersion.getChunkCount(), documentationService.getVersionZip(version));
                long t2 = System.nanoTime();
                ingestChunksFromZip(jdkVersion, zipFile, jdkVersion.getChunkCount(), t1);
                jdkVersion.setStatus(IngestStatus.READY);
                jdkVersion.setIngestedAt(Instant.now());
                log.info("Ingested JDK {}: {} chunks ({}ms)", version, jdkVersion.getChunkCount(), elapsedMs(t2));
                log.info("Total ingest for JDK {}: {} ({}ms)", version, formatDuration(totalStart, System.nanoTime()), elapsedMs(totalStart));
            } catch (RuntimeException | IOException e) {
                jdkVersion.setStatus(IngestStatus.FAILED);
                log.error("Ingestion failed for {}: {} ({})", version, e.getMessage(), elapsedMs(totalStart), e);
                throw e;
            }
        }
        return jdkVersion;
    }

    /**
     * Finds a ZIP entry by filename, searching recursively (entries may be under a version-prefixed directory).
     */
    private ZipEntry findZipEntry(ZipFile zipFile, String name) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.equals(name) || entryName.endsWith("/" + name)) {
                return entry;
            }
        }
        return null;
    }

    private JdkVersion readManifestFromZip(String version, ZipFile zipFile) throws IOException {
        ZipEntry indexEntry = findZipEntry(zipFile, "index.json");
        if (indexEntry == null) {
            throw new IOException("index.json not found in ZIP for version " + version);
        }
        byte[] bytes = zipFile.getInputStream(indexEntry).readAllBytes();
        JsonNode root = jsonMapper.readTree(bytes);
        int[] parsed = JdkDistributionDownloader.parseVersion(version); // {major, minor, security}
        JdkVersion jdkVersion = new JdkVersion();
        jdkVersion.setVersion(version);
        jdkVersion.setMajor(parsed[0]);
        jdkVersion.setMinor(parsed[1]);
        jdkVersion.setSecurity(parsed[2]);
        jdkVersion.setJavaRuntime(str(root, "javaRuntime"));
        jdkVersion.setGenerator(str(root, "generator"));
        String generatedAt = str(root, "generatedAt");
        if (generatedAt != null) {
            jdkVersion.setGeneratedAt(Instant.parse(generatedAt));
        }
        jdkVersion.setTypeCount(root.path("typeCount").asInt());
        jdkVersion.setChunkCount(root.path("chunkCount").asInt());
        jdkVersion.setPackageCount(root.path("packages").size());
        jdkVersion.setModuleCount(root.path("modules").size());
        return jdkVersion;
    }

    private void ingestElementsFromZip(JdkVersion v, ZipFile zipFile, int totalExpected, long startNanos) throws IOException {
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
                if (persistElementFromJson(v, json)) {
                    persisted++;
                }
                if (total % BATCH_SIZE == 0) {
                    jdkDocElementRepository.flush();
                    log.info("[{}/elements] Processed {} / {} entries, persisted {} elements ({}ms elapsed)", v.getVersion(), total, totalExpected, persisted, elapsedMs(startNanos));
                }
            }
        }
        if (total > BATCH_SIZE && total % (BATCH_SIZE * 10) == 0) {
            log.info("[{}/elements] Processed {} / {} entries, persisted {} elements ({}ms elapsed)", v.getVersion(), total, totalExpected, persisted, elapsedMs(startNanos));
        }
    }

    /**
     * Returns true if the element was persisted, false if it was skipped (e.g. missing 'kind' field).
     */
    private boolean persistElementFromJson(JdkVersion v, String json) {
        JsonNode node = jsonMapper.readTree(json);
        JdkDocElement jdkDocElement = new JdkDocElement();
        jdkDocElement.setJdkVersion(v);
        jdkDocElement.setRawJson(json);
        String kind = node.path("kind").asString(null);
        if (kind == null) {
            log.debug("Skipping element without 'kind' field: {}", json);
            return false;
        }
        switch (kind) {
            case "MODULE" -> {
                jdkDocElement.setKind(ElementKind.MODULE);
                jdkDocElement.setModuleName(str(node, "name"));
                jdkDocElement.setQualifiedId("module:" + str(node, "name"));
            }
            case "PACKAGE" -> {
                jdkDocElement.setKind(ElementKind.PACKAGE);
                jdkDocElement.setPackageName(str(node, "name"));
                jdkDocElement.setQualifiedId(str(node, "name"));
            }
            default -> {
                jdkDocElement.setKind(ElementKind.TYPE);
                jdkDocElement.setSimpleName(str(node, "name"));
                jdkDocElement.setPackageName(str(node, "package"));
                jdkDocElement.setModuleName(str(node, "module"));
                jdkDocElement.setQualifiedId(str(node, "qualifiedName"));
            }
        }
        jdkDocElementRepository.save(jdkDocElement);
        return true;
    }

    private void ingestChunksFromZip(JdkVersion v, ZipFile zipFile, int totalChunks, long startNanos) throws IOException {
        ZipEntry chunksEntry = findZipEntry(zipFile, "chunks.jsonl");
        if (chunksEntry == null) {
            log.info("[{}/chunks] No chunks.jsonl found in ZIP", v.getVersion());
            return;
        }
        int count = 0;
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipFile.getInputStream(chunksEntry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = jsonMapper.readTree(line);
                JsonNode meta = node.path("metadata");
                JdkDocChunk jdkDocChunk = new JdkDocChunk();
                jdkDocChunk.setJdkVersion(v);
                jdkDocChunk.setVersion(v.getVersion());
                jdkDocChunk.setChunkId(node.path("id").asString(null));
                jdkDocChunk.setText(node.path("text").asString(null));
                jdkDocChunk.setEmbedding(embeddingService.embedPassage(jdkDocChunk.getText()));
                jdkDocChunk.setKind(str(meta, "kind"));
                jdkDocChunk.setQualifiedType(str(meta, "type"));
                jdkDocChunk.setPackageName(str(meta, "package"));
                jdkDocChunk.setModuleName(str(meta, "module"));
                jdkDocChunk.setMember(str(meta, "member"));
                jdkDocChunk.setSignature(str(meta, "signature"));
                jdkDocChunk.setSince(str(meta, "since"));
                jdkDocChunk.setDeprecated(meta.path("deprecated").asBoolean());
                jdkDocChunk.setSourceFile(str(meta, "file"));
                jdkDocChunk.setSourceLine(meta.path("line").asInt());
                jdkDocChunk.setPart(meta.path("part").asInt());
                jdkDocChunk.setParts(meta.path("parts").asInt());
                jdkDocChunk.setParentChunkId(str(meta, "parentId"));
                // Link to its owning structural element (enclosing type for members, else self).
                String ownerId = str(meta, "type");
                if (ownerId == null) {
                    ownerId = jdkDocChunk.getChunkId();
                }
                jdkDocElementRepository.findByJdkVersionAndQualifiedId(v, ownerId).ifPresent(jdkDocChunk::setJDKDocElement);
                jdkDocChunkRepository.save(jdkDocChunk);
                if (++count % BATCH_SIZE == 0) {
                    jdkDocChunkRepository.flush();
                }
                if (count > BATCH_SIZE && count % (BATCH_SIZE * 10) == 0) {
                    log.info("[{}/chunks] Processed {} / {} chunks ({} skipped, {}ms elapsed)", v.getVersion(), count, totalChunks, skipped, elapsedMs(startNanos));
                }
            }
        }
        log.info("[{}/chunks] Chunk ingestion complete: {} / {} processed, {} skipped ({}ms elapsed)", v.getVersion(), count, totalChunks, skipped, elapsedMs(startNanos));
    }

    /**
     * Returns the field's text, or {@code null} when absent/null (Jackson 3 tree access).
     */
    private static String str(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString("");
    }

    /**
     * Formats elapsed time as a human-readable string.
     */
    private static String formatDuration(long startNanos, long endNanos) {
        long elapsedMs = elapsedMs(startNanos);
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

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
