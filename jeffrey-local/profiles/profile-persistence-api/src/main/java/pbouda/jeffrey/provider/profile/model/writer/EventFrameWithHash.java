package pbouda.jeffrey.provider.profile.model.writer;

import pbouda.jeffrey.provider.profile.model.EventFrame;

public record EventFrameWithHash(long hash, EventFrame frame) {
}
