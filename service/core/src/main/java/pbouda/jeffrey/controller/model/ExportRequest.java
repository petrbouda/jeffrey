package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

public record ExportRequest(String primaryProfileId, String secondaryProfileId, String flamegraphId, EventType eventType, TimeRange timeRange) {
}
