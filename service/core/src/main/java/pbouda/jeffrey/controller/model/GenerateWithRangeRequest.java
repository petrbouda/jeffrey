package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.EventType;

public record GenerateWithRangeRequest(String primaryProfileId, String flamegraphName, EventType eventType, TimeRangeRequest timeRange) {
}
