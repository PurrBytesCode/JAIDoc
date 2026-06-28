package com.purrbyte.ai.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Progress update emitted during artifact download, extraction, or documentation generation.
 *
 * <p>Each {@code Progress} carries a percentage (0.0–100.0) and a {@link #module} identifier
 * that classifies which pipeline phase the progress belongs to. Consumers render the
 * percentage against the module label to display stage-specific progress (e.g. "download: 45%").
 */
@Getter
@Setter
public class Progress {

    /**
     * Module identifier for the download phase (artifact fetch from remote repository).
     */
    public static final String MODULE_DOWNLOAD = "download";

    /**
     * Module identifier for the extraction phase (unpacking downloaded archive).
     */
    public static final String MODULE_EXTRACT = "extract";

    /**
     * Module identifier for the Javadoc generation phase (running JsonDoclet).
     */
    public static final String MODULE_JAVADOC = "javadoc";

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
    public Progress(double percentage, String module) {
        this.percentage = BigDecimal.valueOf(percentage)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        this.module = module;
    }

    /**
     * Factory method for creating a progress update.
     * Equivalent to {@link #Progress(double, String)} — the constructor already
     * rounds the percentage to 2 decimal places.
     */
    public static Progress of(double percentage, String module) {
        return new Progress(percentage, module);
    }
}
