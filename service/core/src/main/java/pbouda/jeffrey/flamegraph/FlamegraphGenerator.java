package pbouda.jeffrey.flamegraph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;

public interface FlamegraphGenerator {

    ObjectNode generate(Path profilePath, EventType type);

    ObjectNode generate(Path profilePath, EventType type, long startMillis, long endMillis);

    void export(Path targetPath, ObjectNode data);

}
