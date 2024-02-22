package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig;

/**
 * Generate a data-file for a timeseries graph from a selected event from JFR file.
 */
public interface TimeseriesGenerator {

    /**
     * Generate a data-file for the timeseries graph based on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a Json representation.
     *
     * @param config all information to generate a heatmap representation of the profiling
     * @return heatmap data represented in byte-array format.
     */
    ArrayNode generate(TimeseriesConfig config);
}
