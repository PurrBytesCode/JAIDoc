package com.purrbyte.ai.service;

import com.purrbyte.ai.domain.JdkVersion;
import com.purrbyte.ai.model.IngestStatus;
import com.purrbyte.ai.repository.JdkVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auto-discovers JDK documentation ZIP files in the configured data directory
 * (recursively, including all subdirectories) and triggers ingestion for any
 * version that has been generated but not yet processed (status != READY in the database).
 * <p>
 * This service runs on application startup when auto-scan is enabled. It walks
 * the configured directory tree looking for {@code *.zip} files at any depth,
 * extracts the version from the filename, checks the database for each version,
 * and calls {@link IngestionService#ingest(String)} for unprocessed versions.
 * </p>
 * <p>
 * Errors during ingestion of one version do NOT block processing of other versions.
 * </p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ingest", name = "enabled", havingValue = "true")
public class IngestDiscoveryService {

    private final IngestionService ingestionService;
    private final JdkVersionRepository versionRepository;

    private final Path dataDirectory;
    private final boolean autoScanEnabled;

    public IngestDiscoveryService(IngestionService ingestionService,
                                  JdkVersionRepository versionRepository,
                                  @Value("${data.directory}") Path dataDirectory,
                                  @Value("${ingest.scan.auto}") boolean autoScanEnabled
    ) {
        this.ingestionService = ingestionService;
        this.versionRepository = versionRepository;
        this.dataDirectory = dataDirectory;
        this.autoScanEnabled = autoScanEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoScanEnabled) {
            log.info("Ingest auto-scan is disabled via ingest.scan.auto=false");
            return;
        }
        if (!Files.isDirectory(dataDirectory)) {
            log.warn("Ingest data directory does not exist: {}", dataDirectory);
            return;
        }
        log.info("Starting ingest auto-discovery in: {}", dataDirectory);
        try (var stream = Files.walk(dataDirectory)) {
            List<String> discoveredVersions = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .forEach(zipPath -> {
                        String fileName = zipPath.getFileName().toString();
                        String version = fileName.substring(0, fileName.length() - 4); // strip .zip
                        discoveredVersions.add(version);
                        log.info("[discovery] Found ZIP for version {} at {}", version, zipPath);
                        processVersion(version);
                    });
            log.info("Ingest auto-discovery complete. Discovered {} version(s) with generated docs.", discoveredVersions.size());
        } catch (IOException e) {
            log.error("Failed to list data directory {}: {}", dataDirectory, e.getMessage(), e);
        }
    }

    /**
     * Checks if a version exists in the database and triggers ingestion if needed.
     */
    private void processVersion(String version) {
        try {
            Optional<JdkVersion> existing = versionRepository.findByVersion(version);
            if (existing.isPresent()) {
                IngestStatus status = existing.get().getStatus();
                if (status == IngestStatus.READY) {
                    log.info("Version {} already ingested (status=READY), skipping", version);
                    return;
                }
                log.info("Version {} found with status={}, triggering re-ingestion", version, status);
            } else {
                log.info("Version {} not found in database, triggering ingestion", version);
            }
            JdkVersion result = ingestionService.ingest(version);
            log.info("Successfully ingested version {} (status={}, chunks={})", result.getVersion(), result.getStatus(), result.getChunkCount());
        } catch (IOException e) {
            log.error("Ingestion failed for version {}: {}", version, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error processing version {}: {}", version, e.getMessage(), e);
        }
    }
}
