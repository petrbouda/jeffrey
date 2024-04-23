package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.EventType;

public record ExportRequest(String primaryProfileId, String secondaryProfileId, String flamegraphId, EventType eventType, TimeRangeRequest timeRange) {
}
