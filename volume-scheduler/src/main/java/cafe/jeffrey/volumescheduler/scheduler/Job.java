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

package cafe.jeffrey.volumescheduler.scheduler;

/**
 * One job of the volume scheduler. A scheduled tick and an operator's run on demand are the same
 * work: {@link JobScheduler} calls {@link #run()} for both, under the job's one lock.
 */
public interface Job {

    JobType type();

    /**
     * One run over every project, to completion. Everything it needs is fixed at construction.
     *
     * @return one line saying what the run did, phrased for the operator who asked for it
     *         ("Deleted 2 sessions, …"); shown verbatim, never interpreted
     */
    String run();
}
