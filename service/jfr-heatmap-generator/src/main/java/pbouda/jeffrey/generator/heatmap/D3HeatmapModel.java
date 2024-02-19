package pbouda.jeffrey.generator.heatmap;

import com.fasterxml.jackson.databind.node.ArrayNode;

public record D3HeatmapModel(int maxvalue, ArrayNode series) {
}
