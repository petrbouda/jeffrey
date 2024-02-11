package pbouda.jeffrey.graph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;

public interface FlamegraphGenerator {

    ObjectNode generate(Path profilePath, EventType type);

    ObjectNode generate(Path profilePath, EventType type, TimeRange timeRange);

}
