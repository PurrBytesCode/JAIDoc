package com.purrbyte.ai.model.dto;

import com.purrbyte.ai.model.TaskStatus;
import lombok.*;

import java.util.UUID;

/**
 * Information about an async task (doc generation or ingest).
 */
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
@Getter
@Setter
public class TaskInfo {

    private final UUID taskId;
    private final String version;
    private TaskStatus status;
    private double progress;
    private String module;
    private String result;
}
