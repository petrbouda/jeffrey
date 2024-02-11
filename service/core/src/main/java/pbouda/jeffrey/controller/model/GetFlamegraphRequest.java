package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record GetFlamegraphRequest(String profileId, String flamegraphId, EventType eventType) {
}
