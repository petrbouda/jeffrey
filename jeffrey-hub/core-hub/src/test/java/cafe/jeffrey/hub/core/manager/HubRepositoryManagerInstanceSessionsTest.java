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

import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One instance's sessions: listed from that instance's rows alone, narrowed by the filter on the
 * headers, and only the survivors walked for their files.
 */
class HubRepositoryManagerInstanceSessionsTest {

    private static final String INSTANCE_ID = "inst-1";
    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

    private final RepositoryStorage repositoryStorage = mock(RepositoryStorage.class);

    private static RecordingSession header(String id, Instant createdAt) {
        return new RecordingSession(id, id, INSTANCE_ID, createdAt, null, RecordingStatus.ACTIVE, List.of(), false);
    }

    private static RecordingSession withFile(RecordingSession session) {
        RepositoryFile file = new RepositoryFile(
                session.id() + "-chunk", "profile-1.jfr", session.createdAt(), 100L, true, null);
        return new RecordingSession(session.id(), session.name(), session.instanceId(), session.createdAt(),
                session.finishedAt(), session.status(), List.of(file), session.retained());
    }

    private HubRepositoryManager manager() {
        return new HubRepositoryManager(mock(ProjectInfo.class), repositoryStorage);
    }

    @Test
    void walksOnlyTheSessionsTheFilterKeeps() {
        RecordingSession newest = header("s-2", T0.plusSeconds(60));
        RecordingSession oldest = header("s-1", T0);
        when(repositoryStorage.listSessionsByInstanceId(INSTANCE_ID, SessionDetail.HEADERS))
                .thenReturn(List.of(newest, oldest));
        when(repositoryStorage.withFiles(any())).thenAnswer(invocation -> withFile(invocation.getArgument(0)));

        List<RecordingSession> sessions = manager()
                .listInstanceSessions(INSTANCE_ID, RecordingSessionFilter.ALL.withLimit(1));

        assertEquals(1, sessions.size());
        assertEquals("s-2", sessions.getFirst().id());
        assertEquals(1, sessions.getFirst().files().size());
        verify(repositoryStorage, never()).withFiles(oldest);
        verify(repositoryStorage, never()).listSessions(any());
    }
}
