package pbouda.jeffrey.graph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Config;

public interface FlamegraphGenerator {

    ObjectNode generate(Config configPath);

}
