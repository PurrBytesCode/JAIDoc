package com.purrbyte.ai.model;

/**
 * Status of an async task (doc generation or ingest).
 */
public enum TaskStatus {

    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
