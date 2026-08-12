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

package cafe.jeffrey.hub.core.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.shared.common.model.job.JobType;

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
 * <p>Runs are not serialized against the scheduler: a manual run can overlap the same job's
 * scheduled tick. Every triggerable job must therefore be safe to run concurrently with itself,
 * which the reconciler is — materialization is guarded by natural keys, so a doubled run
 * materializes nothing twice.</p>
 */
public class ManualJobRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ManualJobRunner.class);

    private final Map<JobType, ManuallyTriggerable> triggerable;
    private final Clock clock;

    public ManualJobRunner(List<Job> jobs, Clock clock) {
        this.triggerable = index(jobs);
        this.clock = clock;
    }

    private static Map<JobType, ManuallyTriggerable> index(List<Job> jobs) {
        Map<JobType, ManuallyTriggerable> byType = new EnumMap<>(JobType.class);
        for (Job job : jobs) {
            if (job instanceof ManuallyTriggerable manual) {
                byType.put(job.jobType(), manual);
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
        ManuallyTriggerable job = triggerable.get(jobType);
        if (job == null) {
            throw new ManualRunNotSupportedException(jobType);
        }

        Instant startedAt = clock.instant();
        LOG.info("Manual job run started: job_type={}", jobType);

        Elapsed<String> elapsed = Measuring.s(job::runManually);

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
