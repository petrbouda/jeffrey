/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.shared.volume.liveness;

import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads the liveness files the Jeffrey agent or the {@code jeffrey-heartbeat} library writes into
 * {@code {session}/.heartbeat/}: the periodic {@code heartbeat} file and the clean-exit
 * {@code finished} marker. Both contain epoch millis as plain text.
 *
 * <p>Every read answers with a {@link LivenessRead}, which separates "nothing was written here"
 * from "something is here and could not be read". Only the first is evidence a session ended, and
 * the caller acts on the difference — see {@link SessionLiveness}.</p>
 */
final class HeartbeatReader {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatReader.class);

    private static final String REASON_UNREADABLE = "the file could not be read";
    private static final String REASON_EMPTY = "the file is empty";
    private static final String REASON_NOT_A_TIMESTAMP = "the content is not epoch millis";

    private final VolumeAccess volume;

    HeartbeatReader(VolumeAccess volume) {
        this.volume = volume;
    }

    /**
     * Reads the last heartbeat timestamp from the heartbeat file.
     *
     * @param session the session's directory
     */
    LivenessRead readLastHeartbeat(VolumePath session) {
        return read(session
                .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                .resolve(HeartbeatConstants.HEARTBEAT_FILE));
    }

    /**
     * Reads the clean-exit marker, written when the library is closed.
     *
     * @param session the session's directory
     */
    LivenessRead readFinishedMarker(VolumePath session) {
        return read(session
                .resolve(HeartbeatConstants.HEARTBEAT_DIR)
                .resolve(HeartbeatConstants.FINISHED_FILE));
    }

    /**
     * Reads without looking first, so that the answer comes from one call rather than from a check
     * and a read that can disagree. It also keeps the two failures apart: only a missing file reads
     * as empty, while a directory this reader may not traverse and a stale handle on a network
     * mount arrive as an exception — which an existence check would have flattened into "not there".
     */
    private LivenessRead read(VolumePath file) {
        String content;
        try {
            Optional<byte[]> bytes = volume.read(file);
            if (bytes.isEmpty()) {
                return LivenessRead.absent();
            }
            content = new String(bytes.get(), StandardCharsets.UTF_8).strip();
        } catch (RuntimeException e) {
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
