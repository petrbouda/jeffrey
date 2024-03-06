package pbouda.jeffrey.graph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;

public interface DiffgraphGenerator {

    record Request(Path baselinePath, Path comparisonPath, EventType eventType, TimeRange timeRange) {

        public Request(Path baselinePath, Path comparisonPath, EventType eventType) {
            this(baselinePath, comparisonPath, eventType, null);
        }
    }

    ObjectNode generate(Request request);

}
