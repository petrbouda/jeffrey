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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.SessionFileDetectorProjectJobDescriptor;
import cafe.jeffrey.hub.core.workspace.QueueWorkspaceEventPublisher;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventSerializer;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.persistentqueue.DuckDBPersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the scanner's central claim end to end: it is stateless, re-offers every candidate on
 * every tick, and the event queue's unique dedup index is what keeps that from producing duplicate
 * events. A mocked publisher cannot show this — only a real queue can.
 */
@DuckDBTest(migration = "classpath:db/migration/server")
class SessionFileDetectorProjectJobIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Duration MAX_FILE_AGE = Duration.ofDays(7);
    private static final Duration SETTLE_THRESHOLD = Duration.ofSeconds(10);

    private static final String WORKSPACE_ID = "ws-1";
    private static final String SESSION_ID = "session-1";
    private static final String INSTANCE_ID = "inst-1";
    private static final String QUEUE_NAME = "workspace_events";
    private static final String CONSUMER_ID = "test-consumer";

    private static final SessionFileDetectorProjectJobDescriptor DESCRIPTOR =
            new SessionFileDetectorProjectJobDescriptor(MAX_FILE_AGE, SETTLE_THRESHOLD, 500);

    private record Fixture(
            SessionFileDetectorProjectJob job,
            ProjectManager projectManager,
            RepositoryStorage storage,
            DuckDBPersistentQueue<WorkspaceEvent> queue) {

        void tick() {
            job.executeOnRepository(projectManager, storage, DESCRIPTOR, JobContext.EMPTY);
        }

        List<QueueEntry<WorkspaceEvent>> drain() {
            return queue.poll(WORKSPACE_ID, CONSUMER_ID);
        }

        long totalEvents() {
            return queue.count(WORKSPACE_ID);
        }
    }

    private static Path writeFile(Path dir, String name, Instant modifiedAt) {
        try {
            Path path = dir.resolve(name);
            Files.writeString(path, "payload");
            Files.setLastModifiedTime(path, FileTime.from(modifiedAt));
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Fixture fixture(DataSource dataSource, Path sessionDir, List<RepositoryFile> files) {
        var provider = new DatabaseClientProvider(dataSource);
        var queue = new DuckDBPersistentQueue<WorkspaceEvent>(
                provider, QUEUE_NAME, new WorkspaceEventSerializer(), FIXED_CLOCK);
        var publisher = new QueueWorkspaceEventPublisher(queue);

        ProjectInfo projectInfo = new ProjectInfo(
                "proj-1", null, "my-project", null, null, WORKSPACE_ID, NOW, NOW, Map.of(), null);

        var projectManager = mock(ProjectManager.class);
        when(projectManager.info()).thenReturn(projectInfo);

        RecordingSession withoutFiles = new RecordingSession(
                SESSION_ID, SESSION_ID, INSTANCE_ID, NOW.minusSeconds(1200), NOW.minusSeconds(60),
                RecordingStatus.FINISHED, sessionDir, null, List.of(), false);
        RecordingSession withFiles = new RecordingSession(
                SESSION_ID, SESSION_ID, INSTANCE_ID, NOW.minusSeconds(1200), NOW.minusSeconds(60),
                RecordingStatus.FINISHED, sessionDir, null, files, false);

        var storage = mock(RepositoryStorage.class);
        when(storage.listSessions(false)).thenReturn(List.of(withoutFiles));
        when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.of(withFiles));

        var storageFactory = mock(RepositoryStorage.Factory.class);
        when(storageFactory.apply(any())).thenReturn(storage);

        var job = new SessionFileDetectorProjectJob(
                mock(WorkspacesManager.class), storageFactory, DESCRIPTOR,
                Duration.ofSeconds(30), FIXED_CLOCK, publisher);

        return new Fixture(job, projectManager, storage, queue);
    }

    private static RepositoryFile recording(Path dir, String id, Instant createdAt) {
        return new RepositoryFile(id, id + ".jfr", createdAt, 4096L,
                SupportedRecordingFile.JFR, RecordingStatus.FINISHED,
                writeFile(dir, id + ".jfr", createdAt));
    }

    @Nested
    class Idempotency {

        @Test
        void repeatedTicksProduceNoDuplicateEvents(DataSource dataSource, @TempDir Path sessionDir) {
            var fixture = fixture(dataSource, sessionDir, List.of(
                    recording(sessionDir, "chunk-1", NOW.minusSeconds(500)),
                    recording(sessionDir, "chunk-2", NOW.minusSeconds(400))));

            fixture.tick();
            long afterFirst = fixture.totalEvents();

            fixture.tick();
            fixture.tick();

            assertAll(
                    () -> assertEquals(2L, afterFirst, "Both chunks announced on the first tick"),
                    () -> assertEquals(2L, fixture.totalEvents(),
                            "Re-offering the same candidates must insert nothing — this is the whole "
                                    + "reason the scanner needs no watermark state"));
        }

        @Test
        void aNewChunkBetweenTicksIsAnnouncedOnce(DataSource dataSource, @TempDir Path sessionDir) {
            var first = recording(sessionDir, "chunk-1", NOW.minusSeconds(500));
            var second = recording(sessionDir, "chunk-2", NOW.minusSeconds(400));

            var initial = fixture(dataSource, sessionDir, List.of(first));
            initial.tick();
            assertEquals(1L, initial.totalEvents());

            var grown = fixture(dataSource, sessionDir, List.of(first, second));
            grown.tick();
            grown.tick();

            assertEquals(2L, grown.totalEvents(),
                    "Only the newly rotated chunk is added; the already-known one stays deduplicated");
        }

        @Test
        void compressionDoesNotReAnnounceTheChunk(DataSource dataSource, @TempDir Path sessionDir) {
            // RepositoryFile.id() strips the recording extension, so the raw and compressed forms
            // of one chunk share an identity across ticks.
            String chunkId = "profile-20260220-115000";
            Instant createdAt = NOW.minusSeconds(400);

            var raw = new RepositoryFile(chunkId, chunkId + ".jfr", createdAt, 4096L,
                    SupportedRecordingFile.JFR, RecordingStatus.FINISHED,
                    writeFile(sessionDir, chunkId + ".jfr", createdAt));
            var beforeCompression = fixture(dataSource, sessionDir, List.of(raw));
            beforeCompression.tick();
            assertEquals(1L, beforeCompression.totalEvents());

            var compressed = new RepositoryFile(chunkId, chunkId + ".jfr.lz4", createdAt, 1024L,
                    SupportedRecordingFile.JFR_LZ4, RecordingStatus.FINISHED,
                    writeFile(sessionDir, chunkId + ".jfr.lz4", createdAt));
            var afterCompression = fixture(dataSource, sessionDir, List.of(compressed));
            afterCompression.tick();

            assertEquals(1L, afterCompression.totalEvents(),
                    "Compressing a chunk must not look like a new file");
        }

        @Test
        void announcedEventsAreReadableFromTheQueue(DataSource dataSource, @TempDir Path sessionDir) {
            var fixture = fixture(dataSource, sessionDir, List.of(
                    recording(sessionDir, "chunk-1", NOW.minusSeconds(500))));

            fixture.tick();
            List<QueueEntry<WorkspaceEvent>> entries = fixture.drain();

            assertAll(
                    () -> assertEquals(1, entries.size()),
                    () -> assertEquals("chunk-1", entries.getFirst().payload().originEventId()),
                    () -> assertEquals(WORKSPACE_ID, entries.getFirst().payload().workspaceRefId()));
        }
    }
}
