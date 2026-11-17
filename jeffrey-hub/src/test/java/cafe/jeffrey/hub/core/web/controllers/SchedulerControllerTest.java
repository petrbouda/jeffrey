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

import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.hub.model.job.JobInfo;
import cafe.jeffrey.hub.model.job.JobType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static cafe.jeffrey.hub.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/jobs}: a job is addressed by its key, the one its properties use, and every error
 * names its status and code.
 */
@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-10T10:00:00Z");

    private static final JobInfo RETENTION = new JobInfo(
            JobType.PROJECT_RETENTION, Duration.ofMinutes(5), Map.of("max-size", "20G"), true, true);
    private static final JobInfo COMPRESSION = new JobInfo(
            JobType.REPOSITORY_JFR_COMPRESSION, Duration.ofMinutes(1), Map.of(), false, false);

    @Mock
    JobRegistry jobRegistry;

    @Mock
    ManualJobRunner manualJobRunner;

    private MockMvcTester mvc() {
        return mockMvcTesterFor(new SchedulerController(jobRegistry, manualJobRunner));
    }

    @Nested
    class Jobs {

        @Test
        void listsEveryJobByItsKeyWithItsResolvedConfiguration() {
            when(jobRegistry.all()).thenReturn(List.of(RETENTION, COMPRESSION));

            assertThat(mvc().get().uri("/api/v1/jobs"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$[0].id", v -> assertThat(v).asString().isEqualTo("project-retention"))
                    .hasPathSatisfying("$[0].enabled", v -> assertThat(v).asBoolean().isTrue())
                    .hasPathSatisfying("$[0].period", v -> assertThat(v).asString().isEqualTo("PT5M"))
                    .hasPathSatisfying("$[0].manualRun", v -> assertThat(v).asBoolean().isTrue())
                    .hasPathSatisfying("$[0].params.max-size", v -> assertThat(v).asString().isEqualTo("20G"))
                    .hasPathSatisfying("$[1].id", v -> assertThat(v).asString().isEqualTo("repository-jfr-compression"))
                    .hasPathSatisfying("$[1].enabled", v -> assertThat(v).asBoolean().isFalse())
                    .hasPathSatisfying("$[1].manualRun", v -> assertThat(v).asBoolean().isFalse())
                    .doesNotHavePath("$[0].jobType")
                    .doesNotHavePath("$[0].manualTriggerSupported");
        }

        @Test
        void anotherMethodOnTheListIsNotAllowed() {
            assertThat(mvc().post().uri("/api/v1/jobs"))
                    .hasStatus(405)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("INVALID_REQUEST"));
        }
    }

    @Nested
    class OneJob {

        @Test
        void answersTheJobItsKeyNames() {
            when(jobRegistry.get(JobType.PROJECT_RETENTION)).thenReturn(RETENTION);

            assertThat(mvc().get().uri("/api/v1/jobs/project-retention"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", v -> assertThat(v).asString().isEqualTo("project-retention"))
                    .hasPathSatisfying("$.params.max-size", v -> assertThat(v).asString().isEqualTo("20G"));
        }

        @Test
        void anUnknownJobIsNotFound() {
            assertThat(mvc().get().uri("/api/v1/jobs/project-cleanup"))
                    .hasStatus(404)
                    .bodyJson()
                    .hasPathSatisfying("$.type", v -> assertThat(v).asString().isEqualTo("CLIENT"))
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("SCHEDULER_JOB_NOT_FOUND"))
                    .hasPathSatisfying("$.message", v -> assertThat(v).asString().isEqualTo("No such job: project-cleanup"));
        }

        /** One spelling: the constant's own name is not a key. */
        @Test
        void theEnumNameIsNotAKey() {
            assertThat(mvc().get().uri("/api/v1/jobs/PROJECT_RETENTION"))
                    .hasStatus(404);
        }
    }

    @Nested
    class Run {

        @Test
        void returnsWhatTheJobReported() {
            when(manualJobRunner.run(JobType.PROJECT_RETENTION)).thenReturn(
                    new ManualJobRunner.Result(
                            JobType.PROJECT_RETENTION, STARTED_AT, 4200L, "Deleted 1 session, 0 files and 0 instances"));

            assertThat(mvc().post().uri("/api/v1/jobs/project-retention/run"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.job", v -> assertThat(v).asString().isEqualTo("project-retention"))
                    .hasPathSatisfying("$.startedAt", v -> assertThat(v).asString().isEqualTo("2026-08-10T10:00:00Z"))
                    .hasPathSatisfying("$.durationMs", v -> assertThat(v).asNumber().isEqualTo(4200))
                    .hasPathSatisfying("$.summary",
                            v -> assertThat(v).asString().isEqualTo("Deleted 1 session, 0 files and 0 instances"));
        }

        @Test
        void aJobWithoutAManualRunIsAConflict() {
            when(manualJobRunner.run(JobType.REPOSITORY_JFR_COMPRESSION))
                    .thenThrow(new ManualJobRunner.ManualRunNotSupportedException(JobType.REPOSITORY_JFR_COMPRESSION));

            assertThat(mvc().post().uri("/api/v1/jobs/repository-jfr-compression/run"))
                    .hasStatus(409)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("INVALID_REQUEST"))
                    .hasPathSatisfying("$.message", v -> assertThat(v).asString()
                            .isEqualTo("Job cannot be run manually: repository-jfr-compression"));
        }

        @Test
        void anUnknownJobIsNotFoundAndNothingRuns() {
            assertThat(mvc().post().uri("/api/v1/jobs/project-cleanup/run"))
                    .hasStatus(404)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("SCHEDULER_JOB_NOT_FOUND"));
            verify(manualJobRunner, never()).run(any());
        }

        @Test
        void aGetDoesNotRunTheJob() {
            assertThat(mvc().get().uri("/api/v1/jobs/project-retention/run"))
                    .hasStatus(405);
        }
    }
}
