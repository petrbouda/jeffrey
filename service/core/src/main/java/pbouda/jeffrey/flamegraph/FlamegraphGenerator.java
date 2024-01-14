package pbouda.jeffrey.flamegraph;

public interface FlamegraphGenerator {

    void generate(String jfrName, String graphName, EventType type);

    void generate(String jfrName, String graphName, EventType type, long startMillis, long endMillis);

}
