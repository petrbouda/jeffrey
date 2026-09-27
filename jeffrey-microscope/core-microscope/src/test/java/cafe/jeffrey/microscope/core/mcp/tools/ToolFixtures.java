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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.shared.common.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * The pieces the tool tests build their tools from, with the defaults production passes explicitly:
 * jobs on the shared scheduler with no limit on how many run together and the standard retention cap,
 * and answers that hand a client that declared tasks its task after the standard task wait.
 */
public final class ToolFixtures {

    private ToolFixtures() {
    }

    /** Jobs waiting {@code budget}, keeping outcomes for the standard hour on the system clock. */
    public static <K, V> BoundedJobs<K, V> jobs(Duration budget) {
        return jobs(budget, BoundedJobs.COMPLETED_RETENTION, Clock.systemUTC());
    }

    public static <K, V> BoundedJobs<K, V> jobs(Duration budget, Duration retention, Clock clock) {
        return jobs(budget, retention, clock, BoundedJobs.UNBOUNDED_CONCURRENCY);
    }

    public static <K, V> BoundedJobs<K, V> jobs(Duration budget, Duration retention, Clock clock, int maxConcurrent) {
        return new BoundedJobs<>(budget, retention, clock, Schedulers.sharedVirtual(), maxConcurrent,
                BoundedJobs.DEFAULT_MAX_RETAINED);
    }

    /** Jobs run by {@code scheduler}, for a test that decides what scheduling does. */
    public static <K, V> BoundedJobs<K, V> jobs(Duration budget, Duration retention, Clock clock, Executor scheduler) {
        return new BoundedJobs<>(budget, retention, clock, scheduler, BoundedJobs.UNBOUNDED_CONCURRENCY,
                BoundedJobs.DEFAULT_MAX_RETAINED);
    }

    /** Answers that wait {@link BoundedJobs#TASK_WAIT_BUDGET} for a client that declared tasks. */
    public static OperationAnswers answers() {
        return new OperationAnswers(BoundedJobs.TASK_WAIT_BUDGET);
    }
}
