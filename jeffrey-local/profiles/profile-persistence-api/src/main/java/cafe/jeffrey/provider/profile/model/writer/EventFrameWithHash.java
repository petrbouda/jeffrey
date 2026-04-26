package cafe.jeffrey.provider.profile.model.writer;

import cafe.jeffrey.provider.profile.model.EventFrame;

public record EventFrameWithHash(long hash, EventFrame frame) {
}
