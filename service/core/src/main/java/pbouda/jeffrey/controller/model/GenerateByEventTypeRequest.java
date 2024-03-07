package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record GenerateByEventTypeRequest(String primaryProfileId, EventType eventType) {
}
