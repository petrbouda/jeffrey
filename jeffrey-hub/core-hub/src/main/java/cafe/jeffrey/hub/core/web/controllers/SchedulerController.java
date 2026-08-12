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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import cafe.jeffrey.hub.core.scheduler.JobRegistry;
import cafe.jeffrey.hub.core.scheduler.ManualJobRunner;
import cafe.jeffrey.shared.common.model.job.JobInfo;
import cafe.jeffrey.shared.common.model.job.JobType;

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
    public List<JobInfo> jobs() {
        return jobRegistry.all();
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
        try {
            return manualJobRunner.run(JobType.valueOf(jobType));
        } catch (IllegalArgumentException | ManualJobRunner.ManualRunNotSupportedException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No job that can be run manually: " + jobType, e);
        }
    }
}
