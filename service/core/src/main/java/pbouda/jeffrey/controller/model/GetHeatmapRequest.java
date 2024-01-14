package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.flamegraph.EventType;

import java.util.List;

public record GetHeatmapRequest(String profile, EventType eventType) {
}
