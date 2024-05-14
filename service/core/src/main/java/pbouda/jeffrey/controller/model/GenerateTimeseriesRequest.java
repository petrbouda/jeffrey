package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.common.Type;

public record GenerateTimeseriesRequest(
        String primaryProfileId,
        String secondaryProfileId,
        Type eventType,
        String search,
        boolean weightValueMode) {
}
