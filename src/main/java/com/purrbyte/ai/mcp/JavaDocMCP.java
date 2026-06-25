package com.purrbyte.ai.mcp;

import com.purrbyte.ai.model.dto.IngestProgress;
import com.purrbyte.ai.model.dto.JdkSearchResult;
import com.purrbyte.ai.model.dto.Progress;
import com.purrbyte.ai.model.dto.TaskInfo;
import com.purrbyte.ai.service.DocumentationService;
import com.purrbyte.ai.service.IngestionService;
import com.purrbyte.ai.service.JdkSearchService;
import com.purrbyte.ai.util.TaskStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaDocMCP {

    private final JdkSearchService jdkSearchService;
    private final DocumentationService documentationService;
    private final IngestionService ingestionService;
    private final TaskStore taskStore;

    @Tool(description = "List JDK versions whose documentation has been generated")
    public List<String> listVersions() {
        return documentationService.listAvailableVersions();
    }

    @Tool(description = "Semantic search of the JDK Javadoc within a single version")
    public List<JdkSearchResult> searchJavadoc(
            @ToolParam(description = "JDK version to search within, e.g. 25.0.3") String version,
            @ToolParam(description = "Natural language query") String query,
            @ToolParam(description = "Maximum number of results") int topK) {
        return jdkSearchService.search(version, query, topK <= 0 ? 10 : topK);
    }

    @Tool(description = "Start generating JDK documentation for the specified version. Returns a task ID that can be used to track progress.")
    public String startDocGeneration(@ToolParam(description = "JDK version to generate, e.g. 25.0.3") String version) {
        TaskInfo taskInfo = taskStore.createTask(version);
        UUID taskId = taskInfo.getTaskId();
        Consumer<Progress> progressCallback = p -> {
            taskStore.updateProgress(taskId, p.getPercentage(), p.getModule());
            log.info("[{}] {} → {}%", taskId, p.getModule(), p.getPercentage());
        };
        documentationService.generateJdkDocumentation(version, progressCallback)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        taskStore.failTask(taskId, ex.getMessage());
                        log.error("Doc generation failed for {}: {}", version, ex.getMessage());
                    } else {
                        taskStore.completeTask(taskId, "Documentation generated at " + result);
                        log.info("Doc generation completed for {}", version);
                    }
                    taskStore.removeTask(taskId);
                });
        return taskId.toString();
    }

    @Tool(description = "Get progress of a doc generation task")
    public TaskInfo getDocGenerationProgress(@ToolParam(description = "Task ID returned by startDocGeneration") String taskId) {
        TaskInfo taskInfo = taskStore.getTask(UUID.fromString(taskId));
        if (taskInfo == null) {
            throw new IllegalArgumentException("Unknown task ID: " + taskId);
        }
        return taskInfo;
    }

    @Tool(description = "Start ingesting the JDK documentation for the specified version. Returns a task ID that can be used to track progress.")
    public String startIngest(@ToolParam(description = "JDK version to ingest, e.g. 25.0.3") String version) {
        Path zipPath = documentationService.getVersionZip(version);
        if (zipPath == null) {
            throw new IllegalArgumentException("No generated documentation found for version " + version + ". Generate documentation first.");
        }
        TaskInfo taskInfo = taskStore.createTask(version);
        UUID taskId = taskInfo.getTaskId();
        Consumer<IngestProgress> progressCallback = p -> taskStore.updateProgress(taskId, p.getPercentage(), p.getModule());
        ingestionService.ingestAsync(version, progressCallback)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        taskStore.failTask(taskId, ex.getMessage());
                        log.error("Ingestion failed for {}: {}", version, ex.getMessage());
                    } else {
                        taskStore.completeTask(taskId, "Ingested " + result.getChunkCount() + " chunks");
                        log.info("Ingestion completed for {}", version);
                    }
                    taskStore.removeTask(taskId);
                });
        return taskId.toString();
    }

    @Tool(description = "Get progress of an ingest task")
    public TaskInfo getIngestProgress(@ToolParam(description = "Task ID returned by startIngest") String taskId) {
        TaskInfo taskInfo = taskStore.getTask(UUID.fromString(taskId));
        if (taskInfo == null) {
            throw new IllegalArgumentException("Unknown task ID: " + taskId);
        }
        return taskInfo;
    }
}
