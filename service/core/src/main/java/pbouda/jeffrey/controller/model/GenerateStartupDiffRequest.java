package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

public record GenerateStartupDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String name,
        EventType eventType,
        TimeRange timeRange) {
}
