package com.purrbyte.ai.service;

import com.purrbyte.ai.domain.*;
import com.purrbyte.ai.model.ElementKind;
import com.purrbyte.ai.model.IngestStatus;
import com.purrbyte.ai.repository.*;
import com.purrbyte.ai.util.JdkDistributionDownloader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final int BATCH_SIZE = 200;

    private final DocumentationService documentationService;
    private final EmbeddingService embeddingService;
    private final JdkVersionRepository versionRepository;
    private final JdkDocElementRepository jdkDocElementRepository;
    private final JdkDocChunkRepository jdkDocChunkRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public JdkVersion ingest(String version) throws IOException {
        Path zipPath = documentationService.getVersionZip(version);
        if (zipPath == null) {
            throw new IOException("No generated documentation found for version " + version);
        }
        // Idempotent: drop any prior ingest of this version (cascade removes elements + chunks).
        versionRepository.findByVersion(version).ifPresent(versionRepository::delete);
        versionRepository.flush();
        JdkVersion jdkVersion;
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            jdkVersion = readManifestFromZip(version, zipFile);
            versionRepository.save(jdkVersion);
            try {
                ingestElementsFromZip(jdkVersion, zipFile);
                ingestChunksFromZip(jdkVersion, zipFile);
                jdkVersion.setStatus(IngestStatus.READY);
                jdkVersion.setIngestedAt(Instant.now());
                log.info("Ingested JDK {}: {} chunks", version, jdkVersion.getChunkCount());
            } catch (RuntimeException | IOException e) {
                jdkVersion.setStatus(IngestStatus.FAILED);
                log.error("Ingestion failed for {}: {}", version, e.getMessage());
                throw e;
            }
        }
        return jdkVersion;
    }

    private JdkVersion readManifestFromZip(String version, ZipFile zipFile) throws IOException {
        ZipEntry indexEntry = zipFile.getEntry("index.json");
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

    private void ingestElementsFromZip(JdkVersion v, ZipFile zipFile) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".json") && !name.equals("index.json")) {
                byte[] bytes = zipFile.getInputStream(entry).readAllBytes();
                String json = new String(bytes, StandardCharsets.UTF_8);
                persistElementFromJson(v, json);
            }
        }
    }

    private void persistElementFromJson(JdkVersion v, String json) {
        JsonNode node = jsonMapper.readTree(json);
        JdkDocElement jdkDocElement = new JdkDocElement();
        jdkDocElement.setJdkVersion(v);
        jdkDocElement.setRawJson(json);
        String kind = node.path("kind").asString(null);
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
    }

    private void ingestChunksFromZip(JdkVersion v, ZipFile zipFile) throws IOException {
        ZipEntry chunksEntry = zipFile.getEntry("chunks.jsonl");
        if (chunksEntry == null) {
            return;
        }
        int count = 0;
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
            }
        }
    }

    /**
     * Returns the field's text, or {@code null} when absent/null (Jackson 3 tree access).
     */
    private static String str(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString("");
    }
}
