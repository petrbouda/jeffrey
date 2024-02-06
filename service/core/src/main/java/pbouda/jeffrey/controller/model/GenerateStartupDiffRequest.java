package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.controller.TimeRange;

public record GenerateStartupDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String flamegraphName,
        String eventType,
        TimeRange timeRange) {
}
