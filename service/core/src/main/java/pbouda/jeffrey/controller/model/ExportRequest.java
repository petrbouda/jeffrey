package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.Type;

public record ExportRequest(
        String primaryProfileId,
        String secondaryProfileId,
        String flamegraphId,
        Type eventType,
        TimeRangeRequest timeRange,
        boolean threadMode) {
}
