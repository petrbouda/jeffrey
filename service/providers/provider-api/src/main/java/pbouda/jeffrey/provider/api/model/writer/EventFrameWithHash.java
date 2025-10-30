package pbouda.jeffrey.provider.api.model.writer;

import pbouda.jeffrey.provider.api.model.EventFrame;

public record EventFrameWithHash(long hash, EventFrame frame) {
}
