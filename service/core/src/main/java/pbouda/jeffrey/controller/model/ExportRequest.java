package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record ExportRequest(String profileId, String flamegraphId, EventType eventType) {
}
