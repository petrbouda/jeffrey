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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.hub.core.web.response.JobResponse;
import cafe.jeffrey.hub.model.job.JobType;

import java.util.List;

/**
 * The scheduler's jobs: a read-only view of how each one is configured, plus the on-demand run
 * for those that offer one. Job settings themselves are resolved from
 * {@code application.properties} at startup and can only be changed by editing that file and
 * restarting the server.
 */
@RestController
@RequestMapping("/api/internal/scheduler")
public class SchedulerController {

    private final JobRegistry jobRegistry;
    private final ManualJobRunner manualJobRunner;

    public SchedulerController(JobRegistry jobRegistry, ManualJobRunner manualJobRunner) {
        this.jobRegistry = jobRegistry;
        this.manualJobRunner = manualJobRunner;
    }

    @GetMapping("/jobs")
    public List<JobResponse> jobs() {
        return jobRegistry.all().stream()
                .map(JobResponse::from)
                .toList();
    }

    /**
     * Runs a job on demand and reports what it did. Synchronous: manual runs are expected to
     * finish in seconds, and the caller wants the outcome rather than a job id to poll.
     *
     * <p>The job type is resolved here rather than bound as an enum so that an unknown name and a
     * job that offers no manual run answer the same way — from the client's side both mean there
     * is no such run to request.</p>
     */
    @PostMapping("/jobs/{jobType}/run")
    public ManualJobRunner.Result run(@PathVariable("jobType") String jobType) {
        JobType type;
        try {
            type = JobType.valueOf(jobType);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No job that can be run manually: " + jobType, e);
        }
        try {
            return manualJobRunner.run(type);
        } catch (ManualJobRunner.ManualRunNotSupportedException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No job that can be run manually: " + jobType, e);
        }
    }
}
