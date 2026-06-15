package com.purrbyte.ai.model;

/**
 * Progress update for the JDK documentation generation pipeline.
 *
 * @param percentage progress percentage within the phase (0.0 to 100.0)
 * @param module     name of the process/module performing the progress
 */
public record Progress(double percentage, String module) {

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

    /**
     * Computes the overall progress percentage across all phases.
     *
     * <p>The pipeline is:
     * <ol>
     *   <li>Download — 0% to 40%</li>
     *   <li>Extract — 40% to 55%</li>
     *   <li>Javadoc — 55% to 100%</li>
     * </ol>
     *
     * @return overall progress (0.0 to 100.0), or the raw percentage if module is null
     */
    public double overallProgress() {
        if (module == null) return percentage;
        switch (module) {
            case MODULE_DOWNLOAD:
                return percentage * 0.40;
            case MODULE_EXTRACT:
                return 40.0 + percentage * 0.15;
            case MODULE_JAVADOC:
                return 55.0 + percentage * 0.45;
            default:
                return percentage;
        }
    }
}
