package com.purrbyte.ai.util;

import com.purrbyte.ai.model.TaskStatus;
import com.purrbyte.ai.model.dto.TaskInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TaskStore {

    private final ConcurrentHashMap<UUID, TaskInfo> tasks = new ConcurrentHashMap<>();

    /**
     * Creates a new task and registers it in the store.
     *
     * @param version the JDK version this task is for
     * @return the newly created task info
     */
    public TaskInfo createTask(String version) {
        UUID taskId = UUID.randomUUID();
        TaskInfo taskInfo = new TaskInfo(taskId, version);
        tasks.put(taskId, taskInfo);
        log.info("Created task {} for version {}", taskId, version);
        return taskInfo;
    }

    /**
     * Updates the progress of a running task.
     */
    public void updateProgress(UUID taskId, double progress, String module) {
        TaskInfo taskInfo = tasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setProgress(progress);
            taskInfo.setModule(module);
            taskInfo.setStatus(TaskStatus.RUNNING);
        }
    }

    /**
     * Marks a task as completed with a result message.
     */
    public void completeTask(UUID taskId, String result) {
        TaskInfo taskInfo = tasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setStatus(TaskStatus.COMPLETED);
            taskInfo.setProgress(100.0);
            taskInfo.setResult(result);
        }
    }

    /**
     * Marks a task as failed with an error message.
     */
    public void failTask(UUID taskId, String error) {
        TaskInfo taskInfo = tasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setStatus(TaskStatus.FAILED);
            taskInfo.setResult(error);
        }
    }

    /**
     * Retrieves a task by its ID.
     */
    public TaskInfo getTask(UUID taskId) {
        return tasks.get(taskId);
    }

    /**
     * Removes a task from the store.
     */
    public void removeTask(UUID taskId) {
        tasks.remove(taskId);
    }
}
