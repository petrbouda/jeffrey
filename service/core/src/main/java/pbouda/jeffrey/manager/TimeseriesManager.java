package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.time.Instant;
import java.util.function.Function;

public interface TimeseriesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, TimeseriesManager> {
    }

    /**
     * Generates a whole timeseries for the given event-type.
     *
     * @param eventType type of the samples in the timeseries
     * @return time + samples in the array format compatible with timeseries graphs
     */
    ArrayNode contentByEventType(Type eventType);

    /**
     * Generates a timeseries for the given event-type bounded by the interval.
     *
     * @param eventType type of the samples in the timeseries
     * @param start     start of the interval for generated output.
     * @param end       enf of the interval for generated output.
     * @return time + samples in the array format compatible with timeseries graphs
     */
    ArrayNode contentByEventType(Type eventType, Instant start, Instant end);
}
