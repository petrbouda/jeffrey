/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.platform.streaming;

import jdk.jfr.consumer.EventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.EventTypeName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Replays a streaming repository to extract the last heartbeat timestamp.
 * Used during reconciliation to recover heartbeat data for sessions that were
 * auto-closed during Jeffrey's downtime.
 */
public class HeartbeatReplayReader {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatReplayReader.class);

    private static final String STREAMING_REPO_DIR = "streaming-repo";

    /**
     * Replays the streaming repository from the given start time and returns the last heartbeat
     * timestamp, or empty if no heartbeat events found or the directory doesn't exist.
     *
     * @param sessionPath path to the session directory
     * @param startFrom   the earliest timestamp to replay from (e.g. session's originCreatedAt)
     * @param clock       the clock to use for the end time
     * @return the last heartbeat timestamp, or empty
     */
    public static Optional<Instant> readLastHeartbeat(Path sessionPath, Instant startFrom, Clock clock) {
        Path streamingRepoPath = sessionPath.resolve(STREAMING_REPO_DIR);
        if (!Files.isDirectory(streamingRepoPath)) {
            return Optional.empty();
        }

        AtomicReference<Instant> lastHeartbeat = new AtomicReference<>();
        try (EventStream stream = EventStream.openRepository(streamingRepoPath)) {
            stream.setStartTime(startFrom);
            stream.setEndTime(clock.instant());
            stream.onEvent(EventTypeName.HEARTBEAT, event ->
                    lastHeartbeat.set(event.getEndTime()));
            stream.start();
        } catch (IOException e) {
            LOG.warn("Failed to replay streaming repo for heartbeat reconciliation: path={}", streamingRepoPath, e);
            return Optional.empty();
        }
        return Optional.ofNullable(lastHeartbeat.get());
    }
}
