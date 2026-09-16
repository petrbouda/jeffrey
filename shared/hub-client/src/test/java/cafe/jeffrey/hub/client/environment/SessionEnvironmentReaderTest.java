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
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader's job is to pick the chunk, pull it and hand it to the parser without ever letting
 * a hub failure reach the detail page — the session metadata beside it came from a call that
 * succeeded, and the environment is decoration on top of it.
 */
class SessionEnvironmentReaderTest {

    private static final String SESSION_ID = "session-1";
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @TempDir
    Path tempHome;

    private SessionEnvironmentParser parser;

    @BeforeEach
    void setUp() {
        parser = new SessionEnvironmentParser(TempDirFactory.of(tempHome));
    }

    private static Path fixtureJfr() {
        try {
            return Path.of(SessionEnvironmentReaderTest.class.getClassLoader()
                    .getResource("jfrs/profile-1.jfr").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve the JFR fixture", e);
        }
    }

    private static RepositoryFile recording(String id, Instant createdAt, RecordingStatus status) {
        return new RepositoryFile(
                id, id + ".jfr", createdAt, 1L, SupportedRecordingFile.JFR, status, Path.of(id + ".jfr"));
    }

    private static RepositoryFile artifact(String id) {
        return new RepositoryFile(
                id, id + ".log", T0, 1L, SupportedRecordingFile.APP_LOG, RecordingStatus.FINISHED,
                Path.of(id + ".log"));
    }

    private static RecordingSession session(RepositoryFile... files) {
        return new RecordingSession(
                SESSION_ID, SESSION_ID, "instance-1", T0, null, RecordingStatus.ACTIVE, null,
                List.of(files), false);
    }

    @Nested
    class WhenThereIsAChunkToRead {

        @Test
        void parsesTheNewestFinishedChunk() {
            FakeRepositoryManager hub = new FakeRepositoryManager(session(
                    recording("chunk-1", T0, RecordingStatus.FINISHED),
                    recording("chunk-2", T0.plusSeconds(60), RecordingStatus.FINISHED),
                    recording("chunk-3", T0.plusSeconds(120), RecordingStatus.ACTIVE)));

            Optional<ObjectNode> environment =
                    new SessionEnvironmentReader(hub, parser).forSession(SESSION_ID, false);

            assertTrue(environment.isPresent());
            assertFalse(environment.get().isEmpty(), "the fixture carries one-shot configuration events");
            assertEquals(List.of("chunk-2"), hub.streamed,
                    "the newest CLOSED chunk is the one read; the open one would parse short");
        }

        @Test
        void cleansUpTheDownloadedChunk() {
            FakeRepositoryManager hub = new FakeRepositoryManager(session(
                    recording("chunk-1", T0, RecordingStatus.FINISHED)));

            new SessionEnvironmentReader(hub, parser).forSession(SESSION_ID, false);

            assertTrue(hub.cleanedUp, "the temp directory holding the download must be released");
        }
    }

    @Nested
    class WhenThereIsNot {

        @Test
        void isEmptyWhenNoChunkHasBeenClosedYet() {
            FakeRepositoryManager hub = new FakeRepositoryManager(session(
                    recording("chunk-1", T0, RecordingStatus.ACTIVE)));

            Optional<ObjectNode> environment =
                    new SessionEnvironmentReader(hub, parser).forSession(SESSION_ID, false);

            assertTrue(environment.isEmpty());
            assertTrue(hub.streamed.isEmpty(), "nothing to download");
        }

        @Test
        void isEmptyWhenTheSessionHasOnlyArtifacts() {
            FakeRepositoryManager hub = new FakeRepositoryManager(session(artifact("app")));

            Optional<ObjectNode> environment =
                    new SessionEnvironmentReader(hub, parser).forSession(SESSION_ID, false);

            assertTrue(environment.isEmpty(), "a log is not a chunk to read an environment from");
        }

        @Test
        void isEmptyWhenTheHubCannotBeReached() {
            FakeRepositoryManager hub = new FakeRepositoryManager(new IllegalStateException("hub is down"));

            Optional<ObjectNode> environment =
                    new SessionEnvironmentReader(hub, parser).forSession(SESSION_ID, false);

            assertTrue(environment.isEmpty(), "a detail page renders perfectly well without an environment");
        }

        @Test
        void isEmptyWhenTheDownloadFails() {
            FakeRepositoryManager hub = new FakeRepositoryManager(session(
                    recording("chunk-1", T0, RecordingStatus.FINISHED)));
            hub.failStreaming = true;

            Optional<ObjectNode> environment =
                    new SessionEnvironmentReader(hub, parser).forSession(SESSION_ID, false);

            assertTrue(environment.isEmpty());
        }
    }

    /**
     * A hub that answers about one session and serves the JFR fixture for whatever chunk is
     * asked for, recording which ones were asked for and whether the download was released.
     */
    private static final class FakeRepositoryManager implements RepositoryManager {

        private final RecordingSession session;
        private final RuntimeException failure;
        private final List<String> streamed = new ArrayList<>();

        private boolean failStreaming;
        private boolean cleanedUp;

        private FakeRepositoryManager(RecordingSession session) {
            this.session = session;
            this.failure = null;
        }

        private FakeRepositoryManager(RuntimeException failure) {
            this.session = null;
            this.failure = failure;
        }

        @Override
        public RecordingSession recordingSession(String sessionId) {
            if (failure != null) {
                throw failure;
            }
            return session;
        }

        @Override
        public StreamedRecordingFile streamFile(String sessionId, String fileId) {
            throw new UnsupportedOperationException(
                    "the reader holds the chunk already and must not make the hub list the session twice");
        }

        @Override
        public StreamedRecordingFile streamFile(String sessionId, RepositoryFile file) {
            if (failStreaming) {
                throw new IllegalStateException("download failed: " + file.id());
            }
            streamed.add(file.id());
            return new StreamedRecordingFile(file.name(), fixtureJfr(), () -> cleanedUp = true);
        }

        @Override
        public List<RecordingSession> listRecordingSessions(boolean withFiles, RecordingSessionFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RepositoryStatistics calculateRepositoryStatistics() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRecordingSession(String recordingSessionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSessionRetained(String recordingSessionId, boolean retained) {
            throw new UnsupportedOperationException();
        }
    }
}
