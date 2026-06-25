package com.purrbyte.ai.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Progress update for the JDK ingestion pipeline.
 */
@Getter
@Setter
public class IngestProgress {

    /**
     * Phase: reading manifest from ZIP
     */
    public static final String MODULE_MANIFEST = "manifest";

    /**
     * Phase: ingesting structural elements (modules, packages, types)
     */
    public static final String MODULE_ELEMENTS = "elements";

    /**
     * Phase: ingesting chunks and generating embeddings
     */
    public static final String MODULE_CHUNKS = "chunks";

    /**
     * Progress percentage within the phase (0.0 to 100.0).
     */
    private final double percentage;

    /**
     * Name of the process/module performing the progress.
     */
    private final String module;

    /**
     * Creates a progress update.
     *
     * @param percentage progress percentage within the phase (0.0 to 100.0)
     * @param module     name of the process/module performing the progress
     */
    public IngestProgress(double percentage, String module) {
        this.percentage = BigDecimal.valueOf(percentage)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        this.module = module;
    }

    /**
     * Factory method for creating a progress update.
     * Equivalent to {@link #IngestProgress(double, String)} — the constructor already
     * rounds the percentage to 2 decimal places.
     */
    public static IngestProgress of(double percentage, String module) {
        return new IngestProgress(percentage, module);
    }
}
