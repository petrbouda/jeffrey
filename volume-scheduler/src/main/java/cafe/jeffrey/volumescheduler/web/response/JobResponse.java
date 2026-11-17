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

package cafe.jeffrey.volumescheduler.web.response;

import cafe.jeffrey.volumescheduler.retention.RetentionPolicy;
import cafe.jeffrey.volumescheduler.scheduler.JobStatus;
import cafe.jeffrey.volumescheduler.scheduler.JobType;

/**
 * One job, as {@code GET /api/v1/jobs} lists it. Durations are ISO-8601 ({@code PT5M}), spelled
 * out here rather than left to the JSON mapper.
 *
 * @param id       the job's id, the same one its properties group uses:
 *                 {@code jeffrey.volume-scheduler.<id>.*}
 * @param enabled  whether it runs on its own; every job can be run on demand
 * @param settings what the retention job keeps; {@code null} for a job without settings
 */
public record JobResponse(String id, boolean enabled, String period, RetentionSettingsResponse settings) {

    /**
     * @param maxSizeBytes the disk one project may occupy
     */
    public record RetentionSettingsResponse(
            String sessionRetention,
            int maxSessions,
            String newestSessionFileRetention,
            long maxSizeBytes,
            String instanceRetention) {

        static RetentionSettingsResponse from(RetentionPolicy policy) {
            return new RetentionSettingsResponse(
                    policy.sessionRetention().toString(),
                    policy.maxSessions(),
                    policy.newestSessionFileRetention().toString(),
                    policy.maxProjectBytes(),
                    policy.instanceRetention().toString());
        }
    }

    public static JobResponse from(JobStatus status, RetentionPolicy retentionPolicy) {
        RetentionSettingsResponse settings = status.type() == JobType.RETENTION
                ? RetentionSettingsResponse.from(retentionPolicy)
                : null;
        return new JobResponse(
                status.type().id(),
                status.schedule().enabled(),
                status.schedule().period().toString(),
                settings);
    }
}
