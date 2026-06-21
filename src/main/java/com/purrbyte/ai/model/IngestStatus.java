package com.purrbyte.ai.model;

/**
 * Lifecycle state of a JDK version during ingestion.
 */
public enum IngestStatus {
    /**
     * The version is being ingested or has failed.
     */
    INGESTING,

    /**
     * The version has been successfully ingested and is ready for search.
     */
    READY,

    /**
     * The ingestion process encountered an error.
     */
    FAILED
}
