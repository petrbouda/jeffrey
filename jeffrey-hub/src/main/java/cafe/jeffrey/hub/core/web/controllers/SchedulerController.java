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
import cafe.jeffrey.hub.core.web.response.JobResponse;
import cafe.jeffrey.hub.core.web.response.JobRunResponse;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.shared.common.exception.Exceptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The scheduler's jobs: how each one is configured, and the on-demand run for those that offer
 * one. Job settings are resolved from {@code application.properties} at startup and change only
 * by editing that file and restarting the hub. A job is addressed by its key
 * ({@code project-retention}), the one its properties use.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class SchedulerController {

    private final JobRegistry jobRegistry;
    private final ManualJobRunner manualJobRunner;

    public SchedulerController(JobRegistry jobRegistry, ManualJobRunner manualJobRunner) {
        this.jobRegistry = jobRegistry;
        this.manualJobRunner = manualJobRunner;
    }

    @GetMapping
    public List<JobResponse> jobs() {
        return jobRegistry.all().stream()
                .map(JobResponse::from)
                .toList();
    }

    @GetMapping("/{job}")
    public JobResponse job(@PathVariable("job") String job) {
        return JobResponse.from(jobRegistry.get(jobType(job)));
    }

    /**
     * Runs a job now and reports what it did. Synchronous: a manual run is expected to finish in
     * seconds, and the caller wants the outcome rather than a run id to poll. It takes the same
     * per-job lock as the scheduled tick, so it waits for a tick in flight.
     */
    @PostMapping("/{job}/run")
    public JobRunResponse run(@PathVariable("job") String job) {
        return JobRunResponse.from(manualJobRunner.run(jobType(job)));
    }

    private static JobType jobType(String key) {
        return JobType.ofKey(key)
                .orElseThrow(() -> Exceptions.schedulerJobNotFound(key));
    }
}
