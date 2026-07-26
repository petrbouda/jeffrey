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
import cafe.jeffrey.hub.core.scheduler.job.descriptor.OrphanedSessionCleanerJobDescriptor;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrphanedSessionCleanerJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration GRACE = Duration.ofHours(24);

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    RepositoryStorage.Factory storageFactory;

    @Mock
    RepositoryStorage storage;

    @Mock
    ProjectManager projectManager;

    private OrphanedSessionCleanerJob job;
    private final OrphanedSessionCleanerJobDescriptor descriptor =
            new OrphanedSessionCleanerJobDescriptor(24, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        ProjectInfo projectInfo = new ProjectInfo(
                "proj-1", null, "my-project", null, null, "ws-1", NOW, NOW, java.util.Map.of(), null);

        when(projectManager.info()).thenReturn(projectInfo);
        when(storageFactory.apply(any())).thenReturn(storage);

        job = new OrphanedSessionCleanerJob(
                workspacesManager, storageFactory, descriptor, Duration.ofHours(1), FIXED_CLOCK);
    }

    private void execute() {
        job.executeOnRepository(projectManager, storage, descriptor, JobContext.EMPTY);
    }

    private static Path sessionDir(Path root, String name, Instant modifiedAt) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        Files.createFile(dir.resolve("recording.jfr"));
        Files.setLastModifiedTime(dir, FileTime.from(modifiedAt));
        return dir;
    }

    private static RecordingSession knownSession(String id, Path absolutePath) {
        return new RecordingSession(
                id, id, "inst-1", NOW.minus(Duration.ofDays(2)), null,
                RecordingStatus.FINISHED, absolutePath, null, List.of(), false);
    }

    @Test
    void removesSettledDirectoryWithNoDatabaseRow(@TempDir Path root) throws IOException {
        Path orphan = sessionDir(root, "orphan", NOW.minus(Duration.ofDays(3)));

        when(storage.listSessionDirectoriesOnDisk()).thenReturn(List.of(orphan));
        when(storage.listSessions(false)).thenReturn(List.of());

        execute();

        assertFalse(Files.exists(orphan), "A settled directory with no session row is an orphan");
    }

    @Test
    void keepsDirectoryThatStillHasASessionRow(@TempDir Path root) throws IOException {
        Path known = sessionDir(root, "known", NOW.minus(Duration.ofDays(3)));

        when(storage.listSessionDirectoriesOnDisk()).thenReturn(List.of(known));
        when(storage.listSessions(false)).thenReturn(List.of(knownSession("known", known)));

        execute();

        assertTrue(Files.exists(known), "A directory backed by a session row must never be removed");
    }

    @Test
    void keepsRecentDirectoryWithinGracePeriod(@TempDir Path root) throws IOException {
        // Models a session whose creation event has not been consumed from the queue yet:
        // indistinguishable from an orphan except by age.
        Path pending = sessionDir(root, "pending", NOW.minus(Duration.ofMinutes(5)));

        when(storage.listSessionDirectoriesOnDisk()).thenReturn(List.of(pending));
        when(storage.listSessions(false)).thenReturn(List.of());

        execute();

        assertTrue(Files.exists(pending), "A backlogged session must survive until the grace period elapses");
    }

    @Test
    void keepsDirectoryExactlyAtGraceBoundary(@TempDir Path root) throws IOException {
        Path boundary = sessionDir(root, "boundary", NOW.minus(GRACE));

        when(storage.listSessionDirectoriesOnDisk()).thenReturn(List.of(boundary));
        when(storage.listSessions(false)).thenReturn(List.of());

        execute();

        assertTrue(Files.exists(boundary), "Exactly at the cutoff is not strictly older — keep it");
    }

    @Test
    void removesOnlyTheOrphansInAMixedRepository(@TempDir Path root) throws IOException {
        Path known = sessionDir(root, "known", NOW.minus(Duration.ofDays(3)));
        Path orphan = sessionDir(root, "orphan", NOW.minus(Duration.ofDays(3)));

        when(storage.listSessionDirectoriesOnDisk()).thenReturn(List.of(known, orphan));
        when(storage.listSessions(false)).thenReturn(List.of(knownSession("known", known)));

        execute();

        assertTrue(Files.exists(known));
        assertFalse(Files.exists(orphan));
    }
}
