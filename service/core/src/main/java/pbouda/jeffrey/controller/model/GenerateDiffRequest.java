package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.Type;

public record GenerateDiffRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String flamegraphName,
        Type eventType,
        TimeRangeRequest timeRange) {
}
