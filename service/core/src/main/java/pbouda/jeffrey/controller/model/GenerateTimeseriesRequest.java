package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record GenerateTimeseriesRequest(String primaryProfileId, String secondaryProfileId, EventType eventType, String search) {
}
