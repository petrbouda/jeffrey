package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

public record GenerateDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String flamegraphName,
        EventType eventType,
        TimeRange timeRange) {
}
