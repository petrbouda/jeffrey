package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRange;

public record GenerateStartupDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String name,
        String eventType,
        TimeRange timeRange) {
}
