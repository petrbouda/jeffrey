package pbouda.jeffrey.graph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;

public interface DiffgraphGenerator {

    record Request(Path baselinePath, Path comparisonPath, EventType eventType, TimeRange timeRange) {
    }

    ObjectNode generate(Request request);

}
