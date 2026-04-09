package pbouda.jeffrey.server.core.streaming;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

public record EventStreamSubscription(
        String sessionId,
        Path sessionPath,
        Set<String> eventTypes,
        Instant startTime,
        Instant endTime,
        boolean sendEmptyBatches) {
}
