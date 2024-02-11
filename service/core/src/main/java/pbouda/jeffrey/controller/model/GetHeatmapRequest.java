package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record GetHeatmapRequest(String profileId, String heatmapName, EventType eventType) {
}
