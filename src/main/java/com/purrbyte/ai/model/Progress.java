package com.purrbyte.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Progress update for the JDK documentation generation pipeline.
 */
@Getter
@Setter
public class Progress {

    /**
     * Phase: downloading JDK source
     */
    public static final String MODULE_DOWNLOAD = "download";

    /**
     * Phase: extracting JDK source zip
     */
    public static final String MODULE_EXTRACT = "extract";

    /**
     * Phase: running Javadoc with JsonDoclet
     */
    public static final String MODULE_JAVADOC = "javadoc";

    private final double percentage;
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
