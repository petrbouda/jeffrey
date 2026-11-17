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

package cafe.jeffrey.volumescheduler.compression;

import cafe.jeffrey.volumescheduler.scheduler.JobType;
import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.VolumeProject;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which sessions a compression run visits: per instance, the newest and the one it ended — the
 * only two a run can find a newly complete recording in. Picking them is the volume read's
 * ({@code VolumeProjectTest}); the job asks for two and compresses each.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompressionJobTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

    @Mock
    VolumeProjects projects;

    @Mock
    VolumeProject project;

    @Mock
    RecordingCompressor compressor;

    private CompressionJob job;

    @BeforeEach
    void setUp() {
        when(project.ref()).thenReturn(new ProjectRef("ws-1", "proj-1"));
        when(projects.all()).thenReturn(List.of(project));
        job = new CompressionJob(projects, compressor);
    }

    private static RecordingSession session(String id, String instanceId, int minutes, boolean newest) {
        return new RecordingSession(id, instanceId, T0.plusSeconds(60L * minutes), newest, List.of());
    }

    @Test
    void compressesTheTwoNewestSessionsOfEveryInstance() {
        RecordingSession a3 = session("a-3", "pod-a", 30, true);
        RecordingSession a2 = session("a-2", "pod-a", 20, false);
        RecordingSession b2 = session("b-2", "pod-b", 25, true);
        when(project.newestSessions(2)).thenReturn(List.of(a3, a2, b2));

        job.run();

        verify(compressor).compress(a3);
        verify(compressor).compress(a2);
        verify(compressor).compress(b2);
        verify(project, never()).sessions();
    }

    @Test
    void anEmptyProjectCompressesNothing() {
        when(project.newestSessions(2)).thenReturn(List.of());

        assertEquals("Compressed 0 recordings in 0 sessions across 1 project", job.run());

        verify(compressor, never()).compress(any());
    }

    @Test
    void summarisesWhatItCompressed() {
        RecordingSession a3 = session("a-3", "pod-a", 30, true);
        RecordingSession a2 = session("a-2", "pod-a", 20, false);
        RecordingSession b2 = session("b-2", "pod-b", 25, true);
        when(project.newestSessions(2)).thenReturn(List.of(a3, a2, b2));
        when(compressor.compress(a3)).thenReturn(3);
        when(compressor.compress(a2)).thenReturn(1);

        assertEquals("Compressed 4 recordings in 2 sessions across 1 project", job.run());
    }

    @Test
    void isTheCompressionJob() {
        assertEquals(JobType.COMPRESSION, job.type());
    }
}
