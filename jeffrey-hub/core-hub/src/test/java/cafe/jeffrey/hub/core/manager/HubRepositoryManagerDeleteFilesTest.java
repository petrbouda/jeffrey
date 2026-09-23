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

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
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
class HubRepositoryManagerDeleteFilesTest {

    private static final String SESSION_ID = "session-1";
    private static final Instant SESSION_START = Instant.parse("2026-02-20T12:00:00Z");

    private final RepositoryStorage repositoryStorage = mock(RepositoryStorage.class);

    private static RepositoryFile chunk(String id, long minute) {
        return new RepositoryFile(
                id, id + ".jfr", SESSION_START.plusSeconds(minute * 60), 10L, true, null);
    }

    /** Two chunks, the newer one open while the session is still recording. */
    private HubRepositoryManager managerOf(RecordingStatus status) {
        RecordingSession session = new RecordingSession(
                SESSION_ID, SESSION_ID, "inst-1", SESSION_START, null, status,
                List.of(chunk("profile-1", 0), chunk("profile-2", 10)), false);

        when(repositoryStorage.singleSession(SESSION_ID, SessionDetail.WITH_FILES)).thenReturn(Optional.of(session));

        return new HubRepositoryManager(
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
        HubRepositoryManager manager = managerOf(RecordingStatus.ACTIVE);

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
        when(repositoryStorage.singleSession("nope", SessionDetail.WITH_FILES)).thenReturn(Optional.empty());
        HubRepositoryManager manager = managerOf(RecordingStatus.ACTIVE);

        assertThrows(IllegalArgumentException.class,
                () -> manager.deleteFilesInSession("nope", List.of("profile-1")));
    }
}
