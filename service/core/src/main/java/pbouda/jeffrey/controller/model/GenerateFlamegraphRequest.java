package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.Type;

public record GenerateFlamegraphRequest(
        String primaryProfileId,
        String flamegraphName,
        Type eventType,
        TimeRangeRequest timeRange,
        boolean threadMode) {
}
