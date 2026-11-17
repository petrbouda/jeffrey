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
import cafe.jeffrey.volumescheduler.scheduler.JobScheduler;
import cafe.jeffrey.volumescheduler.scheduler.JobStatus;
import cafe.jeffrey.volumescheduler.scheduler.JobType;
import cafe.jeffrey.volumescheduler.web.response.JobResponse;
import cafe.jeffrey.volumescheduler.web.response.JobRunResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The jobs: how each one is configured, and a run on demand. A job's settings come from the
 * configuration at startup and change only by a restart. A job is addressed by its id
 * ({@code retention}, {@code compression}), the one its properties group uses.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobsController {

    private final JobScheduler scheduler;
    private final RetentionPolicy retentionPolicy;

    public JobsController(JobScheduler scheduler, RetentionPolicy retentionPolicy) {
        this.scheduler = scheduler;
        this.retentionPolicy = retentionPolicy;
    }

    @GetMapping
    public List<JobResponse> jobs() {
        return scheduler.jobs().stream()
                .map(this::response)
                .toList();
    }

    @GetMapping("/{job}")
    public JobResponse job(@PathVariable("job") String job) {
        JobType type = jobType(job);
        return scheduler.job(type)
                .map(this::response)
                .orElseThrow(() -> new UnknownJobException(job));
    }

    /**
     * Runs a job now and reports what it did. Synchronous: the caller wants the outcome rather than
     * a run id to poll. It takes the same lock as the scheduled run, so it waits for one in flight.
     */
    @PostMapping("/{job}/run")
    public JobRunResponse run(@PathVariable("job") String job) {
        return JobRunResponse.from(scheduler.runNow(jobType(job)));
    }

    private JobResponse response(JobStatus status) {
        return JobResponse.from(status, retentionPolicy);
    }

    private static JobType jobType(String id) {
        return JobType.ofId(id).orElseThrow(() -> new UnknownJobException(id));
    }
}
