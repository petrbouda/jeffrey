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

package cafe.jeffrey.hub.core.scheduler.history;

import cafe.jeffrey.shared.common.model.job.JobType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory, bounded history of scheduler job executions for debugging. Keeps the
 * newest {@value #MAX_EXECUTIONS_PER_JOB_TYPE} runs per job type in a ring buffer;
 * everything is lost on restart by design (no persistence).
 * <p>
 * Writes come from the scheduler's single executor thread; reads come from REST
 * request threads, hence the synchronized snapshotting per deque.
 */
public class JobExecutionHistory {

    public static final int MAX_EXECUTIONS_PER_JOB_TYPE = 100;

    private final ConcurrentMap<JobType, ArrayDeque<JobExecution>> executionsByType = new ConcurrentHashMap<>();

    /**
     * Records a finished run, evicting the oldest one once the per-type cap is reached.
     */
    public void add(JobExecution execution) {
        ArrayDeque<JobExecution> executions =
                executionsByType.computeIfAbsent(execution.jobType(), _ -> new ArrayDeque<>());

        synchronized (executions) {
            executions.addFirst(execution);
            while (executions.size() > MAX_EXECUTIONS_PER_JOB_TYPE) {
                executions.removeLast();
            }
        }
    }

    /**
     * Snapshot of all kept executions across all job types, newest first.
     */
    public List<JobExecution> all() {
        List<JobExecution> snapshot = new ArrayList<>();
        for (ArrayDeque<JobExecution> executions : executionsByType.values()) {
            synchronized (executions) {
                snapshot.addAll(executions);
            }
        }
        snapshot.sort(Comparator.comparing(JobExecution::startedAt).reversed());
        return List.copyOf(snapshot);
    }
}
