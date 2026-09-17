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
