package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.Type;

public record GetFlamegraphRequest(String profileId, String flamegraphId, Type eventType) {
}
