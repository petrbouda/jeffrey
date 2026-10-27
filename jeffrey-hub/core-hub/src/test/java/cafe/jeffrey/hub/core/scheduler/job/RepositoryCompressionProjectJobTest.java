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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which sessions a compression tick visits: per instance, the newest and the one it ended — the
 * only two a tick can find a newly complete recording in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepositoryCompressionProjectJobTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryStorage storage;

    private RepositoryCompressionProjectJob job;

    @BeforeEach
    void setUp() {
        when(projectManager.info()).thenReturn(new ProjectInfo("proj-1", "my-project", "ws-1", T0, Map.of()));
        job = new RepositoryCompressionProjectJob(workspacesManager, new JobConfig(true, Duration.ofMinutes(1), Map.of()));
    }

    private static RecordingSession session(String id, String instanceId, int minutes, boolean newest) {
        return new RecordingSession(id, id, instanceId, T0.plusSeconds(60L * minutes), newest, List.of());
    }

    @Test
    void compressesTheTwoNewestSessionsOfEveryInstance() {
        when(storage.listSessions(SessionDetail.HEADERS)).thenReturn(List.of(
                session("a-3", "pod-a", 30, true),
                session("b-2", "pod-b", 25, true),
                session("a-2", "pod-a", 20, false),
                session("b-1", "pod-b", 15, false),
                session("a-1", "pod-a", 10, false)));

        job.executeOnRepository(projectManager, storage);

        verify(storage).compressSession("a-3");
        verify(storage).compressSession("a-2");
        verify(storage).compressSession("b-2");
        verify(storage).compressSession("b-1");
        verify(storage, never()).compressSession("a-1");
    }

    @Test
    void anEmptyProjectCompressesNothing() {
        when(storage.listSessions(SessionDetail.HEADERS)).thenReturn(List.of());

        job.executeOnRepository(projectManager, storage);

        verify(storage, never()).compressSession(anyString());
    }
}
