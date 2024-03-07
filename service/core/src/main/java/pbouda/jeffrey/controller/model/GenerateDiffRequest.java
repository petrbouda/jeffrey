package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

public record GenerateDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        EventType eventType,
        TimeRange timeRange) {
}
