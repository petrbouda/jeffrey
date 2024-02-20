package pbouda.jeffrey.generator.heatmap;

import com.fasterxml.jackson.databind.node.ArrayNode;

public record HeatmapModel(int maxvalue, ArrayNode series) {
}
