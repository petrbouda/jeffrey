package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;
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

    Optional<GraphContent> generateComplete(EventType eventType);

    ObjectNode generate(EventType eventType);

    ObjectNode generate(EventType eventType, TimeRange timeRange);

    ArrayNode timeseries(EventType eventType);

    Optional<GraphContent> generateCustom(EventType eventType, TimeRange timeRange, String name);

    Optional<GraphContent> get(String flamegraphId);

    void export(String flamegraphId);

    void export(EventType eventType);

    void delete(String flamegraphId);

    void cleanup();
}
