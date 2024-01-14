package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.controller.TimeRange;
import pbouda.jeffrey.flamegraph.EventType;

public record GenerateWithRangeRequest(String profile, String flamegraphName, EventType eventType, TimeRange timeRange) {
}
