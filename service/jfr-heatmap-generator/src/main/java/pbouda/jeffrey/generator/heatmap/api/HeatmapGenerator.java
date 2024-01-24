package pbouda.jeffrey.generator.heatmap.api;

import pbouda.jeffrey.generator.heatmap.HeatmapConfig;

/**
 * Generate a data-file for a heatmap from a selected event from JFR file.
 */
public interface HeatmapGenerator {

    /**
     * Generate a data-file for the heatmap base on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a byte-array representation.
     *
     * @param config all information to generate a heatmap representation of the profiling
     * @return heatmap data represented in byte-array format.
     */
    byte[] generate(HeatmapConfig config);
}
