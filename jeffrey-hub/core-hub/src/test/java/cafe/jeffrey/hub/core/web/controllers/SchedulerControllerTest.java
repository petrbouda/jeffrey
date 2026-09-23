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

package cafe.jeffrey.hub.core.web.controllers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.hub.model.job.JobInfo;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.hub.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Mock
    JobRegistry jobRegistry;

    @Mock
    ManualJobRunner manualJobRunner;

    private MockMvcTester mvc() {
        return mockMvcTesterFor(new SchedulerController(jobRegistry, manualJobRunner));
    }

    @Nested
    class Jobs {

        /**
         * The capability travels to the client on every job, which is what lets the table render a
         * run control without knowing which jobs support one.
         */
        @Test
        void reportsWhetherEachJobCanBeRunManually() {
            when(jobRegistry.all()).thenReturn(List.of(
                    new JobInfo(JobType.WORKSPACE_RECONCILER, JobType.ExecutionLevel.GLOBAL,
                            Duration.ofSeconds(5), Map.of(), true, true),
                    new JobInfo(JobType.EXPIRED_INSTANCE_CLEANER, JobType.ExecutionLevel.PROJECT,
                            Duration.ofHours(1), Map.of(), true, false)));

            assertThat(mvc().get().uri("/api/internal/scheduler/jobs"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$[0].manualTriggerSupported", v -> assertThat(v).asBoolean().isTrue())
                    .hasPathSatisfying("$[1].manualTriggerSupported", v -> assertThat(v).asBoolean().isFalse());
        }
    }

    @Nested
    class Run {

        @Test
        void returnsWhatTheJobReported() {
            when(manualJobRunner.run(JobType.WORKSPACE_RECONCILER)).thenReturn(
                    new ManualJobRunner.Result(
                            JobType.WORKSPACE_RECONCILER, STARTED_AT, 4200L, "3 entities from 2 workspaces"));

            assertThat(mvc().post().uri("/api/internal/scheduler/jobs/WORKSPACE_RECONCILER/run"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.jobType", v -> assertThat(v).asString().isEqualTo("WORKSPACE_RECONCILER"))
                    .hasPathSatisfying("$.durationMs", v -> assertThat(v).asNumber().isEqualTo(4200))
                    .hasPathSatisfying("$.summary",
                            v -> assertThat(v).asString().isEqualTo("3 entities from 2 workspaces"));
        }

        @Test
        void aJobWithoutAManualRunIsNotFound() {
            when(manualJobRunner.run(JobType.EXPIRED_INSTANCE_CLEANER))
                    .thenThrow(new ManualJobRunner.ManualRunNotSupportedException(JobType.EXPIRED_INSTANCE_CLEANER));

            assertThat(mvc().post().uri("/api/internal/scheduler/jobs/EXPIRED_INSTANCE_CLEANER/run"))
                    .hasStatus(404);
        }

        @Test
        void anUnknownJobTypeIsRejected() {
            assertThat(mvc().post().uri("/api/internal/scheduler/jobs/NOT_A_JOB/run"))
                    .hasStatus4xxClientError();
        }
    }
}
