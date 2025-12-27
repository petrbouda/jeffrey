/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform.scheduler;

import java.util.concurrent.Future;

public interface Scheduler extends AutoCloseable {

    /**
     * Starts the scheduler.
     */
    void start();

    /**
     * Executes a job immediately with the given context.
     *
     * @param job     the job to execute
     * @param context the execution context containing runtime parameters
     */
    Future<?> submitNow(Job job, JobContext context);

    /**
     * Executes a job immediately with empty context.
     *
     * @param job the job to execute
     */
    default Future<?> submitNow(Job job) {
        return submitNow(job, JobContext.EMPTY);
    }

    /**
     * Submits a job and waits for its completion with the given context.
     *
     * @param job     the job to execute
     * @param context the execution context containing runtime parameters
     */
    void submitAndWait(Job job, JobContext context);

    /**
     * Submits a job and waits for its completion with empty context.
     *
     * @param job the job to execute
     */
    default void submitAndWait(Job job) {
        submitAndWait(job, JobContext.EMPTY);
    }

    /**
     * Shuts down the scheduler. Stops executing the tasks.
     */
    void close();
}
