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

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting files by the ids a caller named.
 *
 * <p>The guard lives here rather than in the storage because this is where ids arrive from
 * outside — the UI's delete, over gRPC. Both retention jobs reach the storage directly, with ids
 * they took from the session's own closed chunks, so they can never name the open one and should
 * not pay for a second listing of the session to be told so.
 */
class RepositoryManagerImplDeleteFilesTest {

    private static final String SESSION_ID = "session-1";
    private static final Instant SESSION_START = Instant.parse("2026-02-20T12:00:00Z");

    private final RepositoryStorage repositoryStorage = mock(RepositoryStorage.class);

    private static RepositoryFile chunk(String id, long minute) {
        return new RepositoryFile(
                id, id + ".jfr", SESSION_START.plusSeconds(minute * 60), 10L, true, null);
    }

    /** Two chunks, the newer one open while the session is still recording. */
    private RepositoryManagerImpl managerOf(RecordingStatus status) {
        RecordingSession session = new RecordingSession(
                SESSION_ID, SESSION_ID, "inst-1", SESSION_START, null, status, null,
                List.of(chunk("profile-1", 0), chunk("profile-2", 10)), false);

        when(repositoryStorage.singleSession(SESSION_ID, true)).thenReturn(Optional.of(session));

        return new RepositoryManagerImpl(
                Clock.fixed(SESSION_START, ZoneOffset.UTC),
                mock(ProjectInfo.class),
                mock(ProjectRepositoryRepository.class),
                mock(ProjectInstanceRepository.class),
                repositoryStorage,
                TransactionOperations.withoutTransaction());
    }

    @Test
    void deletesTheClosedChunksItWasAsked() {
        managerOf(RecordingStatus.ACTIVE).deleteFilesInSession(SESSION_ID, List.of("profile-1"));

        verify(repositoryStorage).deleteRepositoryFiles(SESSION_ID, List.of("profile-1"));
    }

    /**
     * Deleting the chunk the profiler holds open takes the file out from under it: the recording
     * loses the chunk in flight and the profiler writes on to a path with no directory entry.
     */
    @Test
    void refusesTheChunkTheProfilerIsStillWriting() {
        RepositoryManagerImpl manager = managerOf(RecordingStatus.ACTIVE);

        String refusal = assertThrows(IllegalArgumentException.class,
                () -> manager.deleteFilesInSession(SESSION_ID, List.of("profile-1", "profile-2")))
                .getMessage();

        assertTrue(refusal.contains("profile-2.jfr"), "names the file rather than the id: " + refusal);
        verify(repositoryStorage, never()).deleteRepositoryFiles(anyString(), any());
    }

    /**
     * A finished session holds no open chunk, so its newest file is as deletable as the rest.
     */
    @Test
    void deletesTheNewestChunkOnceTheSessionHasFinished() {
        managerOf(RecordingStatus.FINISHED).deleteFilesInSession(SESSION_ID, List.of("profile-2"));

        verify(repositoryStorage).deleteRepositoryFiles(SESSION_ID, List.of("profile-2"));
    }

    @Test
    void refusesASessionItCannotFind() {
        when(repositoryStorage.singleSession("nope", true)).thenReturn(Optional.empty());
        RepositoryManagerImpl manager = managerOf(RecordingStatus.ACTIVE);

        assertThrows(IllegalArgumentException.class,
                () -> manager.deleteFilesInSession("nope", List.of("profile-1")));
    }
}
