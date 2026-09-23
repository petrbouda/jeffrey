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
 * One scheduler job as the Scheduler page lists it. The period travels in ISO-8601
 * ({@code PT5S}), which is what the page has always parsed; the domain record's
 * {@code Duration} serialised to exactly that, and this spells it out rather than relying on it.
 *
 * @param manualTriggerSupported whether an operator may run this job on demand
 */
public record JobResponse(
        String jobType,
        String executionLevel,
        String period,
        Map<String, String> params,
        boolean enabled,
        boolean manualTriggerSupported) {

    public static JobResponse from(JobInfo job) {
        return new JobResponse(
                job.jobType().name(),
                job.executionLevel().name(),
                job.period().toString(),
                job.params(),
                job.enabled(),
                job.manualTriggerSupported());
    }
}
