package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.EventType;

public record GetTimeseriesRequest(String profileId, EventType eventType) {
}
