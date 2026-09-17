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

package cafe.jeffrey.hub.core.project.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Reads the liveness files the {@code jeffrey-heartbeat} library writes into
 * {@code {sessionPath}/.heartbeat/}: the periodic {@code heartbeat} file and
 * the clean-exit {@code finished} marker. Both contain epoch millis as plain text.
 *
 * <p>Every read answers with a {@link LivenessRead}, which separates "nothing was written here"
 * from "something is here and could not be read". Only the first is evidence a session ended, and
 * the caller acts on the difference — see {@link SessionFinisher#tryFinishFromHeartbeat}.</p>
 */
public class FileHeartbeatReader {

    private static final Logger LOG = LoggerFactory.getLogger(FileHeartbeatReader.class);

    private static final String REASON_UNREADABLE = "the file could not be read";
    private static final String REASON_EMPTY = "the file is empty";
    private static final String REASON_NOT_A_TIMESTAMP = "the content is not epoch millis";

    /**
     * Reads the last heartbeat timestamp from the heartbeat file.
     *
     * @param sessionPath path to the session directory
     */
    public LivenessRead readLastHeartbeat(Path sessionPath) {
        return read(sessionPath
                .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                .resolve(HeartbeatConstants.HEARTBEAT_FILE));
    }

    /**
     * Reads the clean-exit marker, written when the library is closed.
     *
     * @param sessionPath path to the session directory
     */
    public LivenessRead readFinishedMarker(Path sessionPath) {
        return read(sessionPath
                .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                .resolve(HeartbeatConstants.FINISHED_FILE));
    }

    /**
     * Reads without stat-ing first, so that the answer comes from one syscall rather than from a
     * check and a read that can disagree. It also keeps the two failures apart: only a missing
     * file reports {@code NoSuchFileException}, while a directory this hub may not traverse and a
     * stale handle on a network mount arrive as an ordinary {@code IOException} — which
     * {@code Files.exists} would have flattened into "not there".
     */
    private static LivenessRead read(Path file) {
        String content;
        try {
            content = Files.readString(file).strip();
        } catch (NoSuchFileException e) {
            return LivenessRead.absent();
        } catch (IOException | RuntimeException e) {
            LOG.warn("Liveness file cannot be read, session left alone: path={}", file, e);
            return LivenessRead.unreadable(REASON_UNREADABLE);
        }

        if (content.isEmpty()) {
            // The writer renames a fully written temporary file into place, so an empty file is a
            // truncated read or a foreign write rather than a state the producer can be in
            LOG.warn("Liveness file is empty, session left alone: path={}", file);
            return LivenessRead.unreadable(REASON_EMPTY);
        }

        try {
            return LivenessRead.reported(Instant.ofEpochMilli(Long.parseLong(content)));
        } catch (NumberFormatException e) {
            LOG.warn("Liveness file does not carry epoch millis, session left alone: path={}", file, e);
            return LivenessRead.unreadable(REASON_NOT_A_TIMESTAMP);
        }
    }
}
