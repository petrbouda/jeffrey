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

package cafe.jeffrey.volumescheduler.web;

import cafe.jeffrey.volumescheduler.retention.RetentionPolicy;
import cafe.jeffrey.volumescheduler.scheduler.JobRun;
import cafe.jeffrey.volumescheduler.scheduler.JobSchedule;
import cafe.jeffrey.volumescheduler.scheduler.JobScheduler;
import cafe.jeffrey.volumescheduler.scheduler.JobStatus;
import cafe.jeffrey.volumescheduler.scheduler.JobType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static cafe.jeffrey.volumescheduler.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/jobs}: a job is addressed by its id, the one its properties group uses, and every
 * error names its status and code.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobsControllerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-10T10:00:00Z");
    private static final long GIB = 1024L * 1024L * 1024L;

    private static final RetentionPolicy POLICY = new RetentionPolicy(
            Duration.ofDays(7), 10, Duration.ofDays(7), 20 * GIB, Duration.ofDays(14));
    private static final JobStatus RETENTION =
            new JobStatus(JobType.RETENTION, new JobSchedule(true, Duration.ofMinutes(5)));
    private static final JobStatus COMPRESSION =
            new JobStatus(JobType.COMPRESSION, new JobSchedule(false, Duration.ofMinutes(1)));

    @Mock
    JobScheduler scheduler;

    private MockMvcTester mvc() {
        when(scheduler.jobs()).thenReturn(List.of(RETENTION, COMPRESSION));
        when(scheduler.job(JobType.RETENTION)).thenReturn(Optional.of(RETENTION));
        when(scheduler.job(JobType.COMPRESSION)).thenReturn(Optional.of(COMPRESSION));
        return mockMvcTesterFor(new JobsController(scheduler, POLICY));
    }

    @Nested
    class Jobs {

        @Test
        void listsEveryJobByItsIdWithItsConfiguration() {
            assertThat(mvc().get().uri("/api/v1/jobs"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$[0].id", v -> assertThat(v).asString().isEqualTo("retention"))
                    .hasPathSatisfying("$[0].enabled", v -> assertThat(v).asBoolean().isTrue())
                    .hasPathSatisfying("$[0].period", v -> assertThat(v).asString().isEqualTo("PT5M"))
                    .hasPathSatisfying("$[0].settings.sessionRetention", v -> assertThat(v).asString().isEqualTo("PT168H"))
                    .hasPathSatisfying("$[0].settings.maxSessions", v -> assertThat(v).asNumber().isEqualTo(10))
                    .hasPathSatisfying("$[0].settings.maxSizeBytes", v -> assertThat(v).asNumber().isEqualTo(20 * GIB))
                    .hasPathSatisfying("$[1].id", v -> assertThat(v).asString().isEqualTo("compression"))
                    .hasPathSatisfying("$[1].enabled", v -> assertThat(v).asBoolean().isFalse())
                    .hasPathSatisfying("$[1].settings", v -> assertThat(v).isNull());
        }

        @Test
        void anotherMethodOnTheListIsNotAllowed() {
            assertThat(mvc().post().uri("/api/v1/jobs"))
                    .hasStatus(405)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("METHOD_NOT_ALLOWED"));
        }
    }

    @Nested
    class OneJob {

        @Test
        void answersTheJobItsIdNames() {
            assertThat(mvc().get().uri("/api/v1/jobs/compression"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", v -> assertThat(v).asString().isEqualTo("compression"))
                    .hasPathSatisfying("$.period", v -> assertThat(v).asString().isEqualTo("PT1M"));
        }

        @Test
        void anUnknownJobIsNotFound() {
            assertThat(mvc().get().uri("/api/v1/jobs/cleanup"))
                    .hasStatus(404)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("JOB_NOT_FOUND"))
                    .hasPathSatisfying("$.message", v -> assertThat(v).asString().isEqualTo("No such job: cleanup"));
        }

        /** One spelling: the constant's own name is not an id. */
        @Test
        void theEnumNameIsNotAnId() {
            assertThat(mvc().get().uri("/api/v1/jobs/RETENTION"))
                    .hasStatus(404);
        }

        @Test
        void aPathWithNoRouteIsNotFound() {
            assertThat(mvc().get().uri("/api/v1/nothing-here"))
                    .hasStatus(404)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("NOT_FOUND"));
        }
    }

    @Nested
    class Run {

        @Test
        void returnsWhatTheJobReported() {
            when(scheduler.runNow(JobType.COMPRESSION)).thenReturn(new JobRun(
                    JobType.COMPRESSION, STARTED_AT, Duration.ofMillis(4200),
                    "Compressed 3 recordings in 1 session across 1 project"));

            assertThat(mvc().post().uri("/api/v1/jobs/compression/run"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.job", v -> assertThat(v).asString().isEqualTo("compression"))
                    .hasPathSatisfying("$.startedAt", v -> assertThat(v).asString().isEqualTo("2026-08-10T10:00:00Z"))
                    .hasPathSatisfying("$.took", v -> assertThat(v).asString().isEqualTo("PT4.2S"))
                    .hasPathSatisfying("$.summary", v -> assertThat(v).asString()
                            .isEqualTo("Compressed 3 recordings in 1 session across 1 project"));
        }

        @Test
        void anUnknownJobIsNotFoundAndNothingRuns() {
            assertThat(mvc().post().uri("/api/v1/jobs/cleanup/run"))
                    .hasStatus(404)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("JOB_NOT_FOUND"));
            verify(scheduler, never()).runNow(any());
        }

        @Test
        void aGetDoesNotRunTheJob() {
            assertThat(mvc().get().uri("/api/v1/jobs/retention/run"))
                    .hasStatus(405);
            verify(scheduler, never()).runNow(any());
        }

        @Test
        void aRunThatFailsIsAnInternalError() {
            when(scheduler.runNow(JobType.RETENTION)).thenThrow(new IllegalStateException("volume went away"));

            assertThat(mvc().post().uri("/api/v1/jobs/retention/run"))
                    .hasStatus(500)
                    .bodyJson()
                    .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("INTERNAL_ERROR"));
        }
    }
}
