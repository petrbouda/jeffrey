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

package cafe.jeffrey.hub.core.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a job on operator demand. Resolves the job from the registered {@link Job} beans, so a job
 * becomes runnable purely by implementing {@link ManuallyTriggerable} — no list to keep in sync.
 *
 * <p>A manual run takes the same per-job lock as the scheduler's tick, so the two never
 * overlap: the request waits for a tick in flight rather than walking the same tree beside it.</p>
 */
public class ManualJobRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ManualJobRunner.class);

    private final Map<JobType, Job> triggerable;
    private final JobLocks jobLocks;
    private final Clock clock;

    public ManualJobRunner(List<Job> jobs, JobLocks jobLocks, Clock clock) {
        this.triggerable = index(jobs);
        this.jobLocks = jobLocks;
        this.clock = clock;
    }

    private static Map<JobType, Job> index(List<Job> jobs) {
        Map<JobType, Job> byType = new EnumMap<>(JobType.class);
        for (Job job : jobs) {
            if (job instanceof ManuallyTriggerable) {
                byType.put(job.jobType(), job);
            }
        }
        return byType;
    }

    /**
     * Job types an operator may run on demand. The registry reports these so the UI can render a
     * control without knowing what any particular job does.
     */
    public Set<JobType> supportedTypes() {
        return triggerable.keySet();
    }

    /**
     * Runs the job and returns what it did.
     *
     * @throws ManualRunNotSupportedException when the job type does not offer a manual run
     */
    public Result run(JobType jobType) {
        Job job = triggerable.get(jobType);
        if (job == null) {
            throw new ManualRunNotSupportedException(jobType);
        }
        ManuallyTriggerable manual = (ManuallyTriggerable) job;

        Instant startedAt = clock.instant();
        LOG.info("Manual job run started: job_type={}", jobType);

        Elapsed<String> elapsed = jobLocks.exclusively(job, () -> Measuring.s(manual::runManually));

        LOG.info("Manual job run finished: job_type={} duration_in_ms={} summary={}",
                jobType, elapsed.duration().toMillis(), elapsed.entity());

        return new Result(jobType, startedAt, elapsed.duration().toMillis(), elapsed.entity());
    }

    /**
     * @param summary one line describing what the run did, written by the job itself
     */
    public record Result(JobType jobType, Instant startedAt, long durationMs, String summary) {
    }

    public static class ManualRunNotSupportedException extends RuntimeException {

        public ManualRunNotSupportedException(JobType jobType) {
            super("Job cannot be run manually: " + jobType);
        }
    }
}
