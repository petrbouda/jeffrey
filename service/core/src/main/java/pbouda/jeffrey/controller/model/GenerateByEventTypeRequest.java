package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.Type;

public record GenerateByEventTypeRequest(String primaryProfileId, Type eventType, boolean threadMode) {
}
