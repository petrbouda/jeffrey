package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.controller.TimeRange;

public record GenerateWithRangeRequest(String profileId, String flamegraphName, String eventType, TimeRange timeRange) {
}
