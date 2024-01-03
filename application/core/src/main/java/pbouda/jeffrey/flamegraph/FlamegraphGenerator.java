package pbouda.jeffrey.flamegraph;

import java.nio.file.Path;

public interface FlamegraphGenerator {

    Path generate(String jfrName, String graphName, EventType type);

}
