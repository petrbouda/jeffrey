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
package cafe.jeffrey.hub.core.web.response;

import cafe.jeffrey.hub.model.job.JobInfo;

import java.util.Map;

/**
 * One scheduler job, as {@code GET /api/v1/jobs} lists it.
 *
 * @param id        the job's key ({@code JobType.key()}), the same one its
 *                  properties use: {@code jeffrey.hub.scheduler.jobs.<id>.*}
 * @param period    ISO-8601 ({@code PT5M}), spelled out rather than left to the JSON mapper
 * @param manualRun whether {@code POST /api/v1/jobs/{id}/run} runs it
 * @param params    the job's parameters as configured, unparsed
 */
public record JobResponse(
        String id,
        boolean enabled,
        String period,
        boolean manualRun,
        Map<String, String> params) {

    public static JobResponse from(JobInfo job) {
        return new JobResponse(
                job.jobType().key(),
                job.enabled(),
                job.period().toString(),
                job.manualTriggerSupported(),
                job.params());
    }
}
