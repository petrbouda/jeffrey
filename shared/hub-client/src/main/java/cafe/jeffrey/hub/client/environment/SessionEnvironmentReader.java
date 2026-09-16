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

package cafe.jeffrey.hub.client.environment;

import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Optional;

/**
 * The one-shot environment events of a hub session, read on this side of the wire.
 *
 * <p>The hub serves the chunk and says nothing about its contents: it pulls the session's newest
 * closed chunk down and parses it here. That is a transfer this call did not use to make — the
 * hub used to parse the file in place and send back the handful of fields — and it is the price
 * of the hub holding no JFR reader. The parse itself is no new work: the hub did exactly this
 * read, decompress and parse on every one of these calls.
 *
 * <p>The chunk is handed to the download already resolved, so the session is listed once rather
 * than twice: a listing walks the session directory and, while it is still recording, opens each
 * recording file to ask the share its size.
 *
 * <p>Nothing is cached. Each call re-fetches, which is what the hub-side version did too, so this
 * is no worse per call than before; a cache keyed on the chunk's id would be a straightforward
 * improvement and is deliberately not bundled with the move.
 */
public class SessionEnvironmentReader {

    private static final Logger LOG = LoggerFactory.getLogger(SessionEnvironmentReader.class);

    private final RepositoryManager repositoryManager;
    private final SessionEnvironmentParser parser;

    public SessionEnvironmentReader(RepositoryManager repositoryManager, SessionEnvironmentParser parser) {
        this.repositoryManager = repositoryManager;
        this.parser = parser;
    }

    /**
     * The environment events of the session's newest closed chunk, or empty when the session has
     * none yet, when the hub can no longer be reached, or when the chunk could not be read.
     *
     * <p>Never propagates a failure: this decorates a detail page that is perfectly readable
     * without it, and the session metadata beside it comes from a different call that succeeded.
     *
     * @param sessionId      the hub session to describe
     * @param expectShutdown whether the session has finished, and so should carry
     *                       {@code jdk.Shutdown} in its final chunk
     */
    public Optional<ObjectNode> forSession(String sessionId, boolean expectShutdown) {
        try {
            RecordingSession session = repositoryManager.recordingSession(sessionId);

            Optional<RepositoryFile> chunk = session.latestFinishedRecording();
            if (chunk.isEmpty()) {
                LOG.debug("Session has no finished recording chunk to read an environment from: session_id={}",
                        sessionId);
                return Optional.empty();
            }

            return Optional.of(parseChunk(sessionId, chunk.get(), expectShutdown));
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the session environment, leaving it off the detail: session_id={}", sessionId, e);
            return Optional.empty();
        }
    }

    private ObjectNode parseChunk(String sessionId, RepositoryFile chunk, boolean expectShutdown) {
        StreamedFile downloaded = repositoryManager.streamFile(sessionId, chunk.id());
        try {
            ObjectNode environment = parser.parse(downloaded.path(), expectShutdown);

            LOG.debug("Read the session environment from a downloaded chunk: session_id={} file_id={} types={}",
                    sessionId, chunk.id(), environment.propertyNames());

            return environment;
        } finally {
            // The download is materialised into a scoped temp directory that nothing else
            // reads: the parse is done with the file by the time this returns.
            cleanUp(downloaded);
        }
    }

    private static void cleanUp(StreamedFile downloaded) {
        if (downloaded.cleanup() == null) {
            return;
        }
        try {
            downloaded.cleanup().close();
        } catch (IOException e) {
            LOG.warn("Cannot remove the downloaded chunk's temp directory: path={}", downloaded.path(), e);
        }
    }
}
