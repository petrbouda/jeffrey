package pbouda.jeffrey.generator.heatmap;

import com.fasterxml.jackson.databind.node.ArrayNode;

public record HeatmapModel(long maxvalue, ArrayNode series) {
}
