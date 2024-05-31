package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.Type;

public record GetHeatmapRequest(String profileId, String heatmapName, Type eventType, boolean useWeight) {
}
