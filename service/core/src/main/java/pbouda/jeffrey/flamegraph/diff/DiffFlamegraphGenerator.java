package pbouda.jeffrey.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;

public interface DiffFlamegraphGenerator {

    record Request(Path baselinePath, Path comparisonPath, EventType eventType, long startMillis, long endMillis) {
    }

    ObjectNode generate(Request request);

}
