package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.Type;

public record GenerateDiffFlamegraphRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String flamegraphName,
        Type eventType,
        TimeRangeRequest timeRange,
        boolean useWeight) {
}
