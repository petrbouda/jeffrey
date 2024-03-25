package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record GetEventsRequest(String primaryProfileId, EventType eventType) {
}
