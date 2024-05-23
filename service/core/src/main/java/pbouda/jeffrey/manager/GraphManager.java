package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface GraphManager {

    @FunctionalInterface
    interface FlamegraphFactory extends Function<ProfileInfo, GraphManager> {
    }

    @FunctionalInterface
    interface DiffgraphFactory extends BiFunction<ProfileInfo, ProfileInfo, GraphManager> {
    }

    List<GraphInfo> allCustom();

    JsonNode supportedEvents();

    ObjectNode generate(Type eventType, TimeRangeRequest timeRange, boolean threadMode);

    void save(Type eventType, TimeRangeRequest timeRange, String flamegraphName);

    ArrayNode timeseries(Type eventType, boolean useWeight);

    ArrayNode timeseries(Type eventType, String searchPattern, boolean useWeight);

    Optional<GraphContent> get(String flamegraphId);

    void export(String flamegraphId);

    void export(Type eventType, TimeRangeRequest timeRange, boolean threadMode);

    void delete(String flamegraphId);

    String generateFilename(Type eventType);
}
