/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import cafe.jeffrey.hub.model.job.JobType;

import java.time.Duration;

public interface Job {

    /**
     * One tick of the job. Every parameter a job needs is fixed at construction from its
     * {@code JobConfig}; there is no per-run input.
     */
    void execute();

    /**
     * The period between executions for periodic jobs.
     */
    Duration period();

    /**
     * The type of job for categorization and logging.
     */
    JobType jobType();
}
