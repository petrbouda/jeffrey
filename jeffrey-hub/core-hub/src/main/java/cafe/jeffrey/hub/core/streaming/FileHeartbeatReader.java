/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads the file-based liveness files written by the agent into
 * {@code {sessionPath}/.heartbeat/}: the periodic {@code heartbeat} file and
 * the clean-exit {@code finished} marker. Both contain epoch millis as plain text.
 */
public class FileHeartbeatReader {

    private static final Logger LOG = LoggerFactory.getLogger(FileHeartbeatReader.class);

    /**
     * Reads the last heartbeat timestamp from the heartbeat file.
     *
     * @param sessionPath path to the session directory
     * @return the last heartbeat instant, or empty if file missing or unreadable
     */
    public Optional<Instant> readLastHeartbeat(Path sessionPath) {
        return readEpochMillisFile(sessionPath
                .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                .resolve(HeartbeatConstants.HEARTBEAT_FILE));
    }

    /**
     * Reads the clean-exit marker written by the agent's shutdown hook.
     *
     * @param sessionPath path to the session directory
     * @return the clean-exit instant, or empty if the marker is missing or unreadable
     */
    public Optional<Instant> readFinishedMarker(Path sessionPath) {
        return readEpochMillisFile(sessionPath
                .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                .resolve(HeartbeatConstants.FINISHED_FILE));
    }

    private static Optional<Instant> readEpochMillisFile(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(file).strip();
            if (content.isEmpty()) {
                return Optional.empty();
            }
            long epochMillis = Long.parseLong(content);
            return Optional.of(Instant.ofEpochMilli(epochMillis));
        } catch (IOException e) {
            LOG.warn("Failed to read heartbeat file: path={}", file, e);
            return Optional.empty();
        } catch (NumberFormatException e) {
            LOG.warn("Invalid heartbeat file content: path={}", file, e);
            return Optional.empty();
        }
    }
}
