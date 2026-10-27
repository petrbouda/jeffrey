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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which sessions a compression tick visits: per instance, the newest and the one it ended — the
 * only two a tick can find a newly complete recording in. Picking them is the volume read's
 * ({@code FilesystemRepositoryStorageTest}); the job asks for two and compresses each.
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
        RecordingSession a3 = session("a-3", "pod-a", 30, true);
        RecordingSession a2 = session("a-2", "pod-a", 20, false);
        RecordingSession b2 = session("b-2", "pod-b", 25, true);
        when(storage.newestSessions(2)).thenReturn(List.of(a3, a2, b2));

        job.executeOnRepository(projectManager, storage);

        verify(storage).compressSession(a3);
        verify(storage).compressSession(a2);
        verify(storage).compressSession(b2);
        verify(storage, never()).listSessions(any());
    }

    @Test
    void anEmptyProjectCompressesNothing() {
        when(storage.newestSessions(2)).thenReturn(List.of());

        job.executeOnRepository(projectManager, storage);

        verify(storage, never()).compressSession(any());
    }
}
