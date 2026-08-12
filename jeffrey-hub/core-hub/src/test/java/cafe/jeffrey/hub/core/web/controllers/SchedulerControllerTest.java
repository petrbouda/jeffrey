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

package cafe.jeffrey.hub.core.web.controllers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.shared.common.model.job.JobInfo;
import cafe.jeffrey.shared.common.model.job.JobType;

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
