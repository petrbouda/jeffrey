package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.EventType;

public record GenerateDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String flamegraphName,
        EventType eventType,
        TimeRangeRequest timeRange) {
}
