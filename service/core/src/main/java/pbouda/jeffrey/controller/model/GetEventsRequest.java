package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.Type;

public record GetEventsRequest(String primaryProfileId, Type eventType) {
}
