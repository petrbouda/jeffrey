/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.api;

import javax.sql.DataSource;
import java.time.Instant;

public interface EventWriter {

    /**
     * Factory for creating EventWriter instances for a profile database.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * @param dataSource         data source of the profile database the events are written into
         * @param profilingStartedAt profiling start of the recording — the zero point of the relative
         *                           event timeline persisted with every event
         */
        EventWriter create(DataSource dataSource, Instant profilingStartedAt);
    }

    /**
     * New single-threaded writer is created for each thread that participates in the writing.
     */
    SingleThreadedEventWriter newSingleThreadedWriter();

    /**
     * This method is called when the writer is completed.
     * It's called always only once. After all threads that participate in the writing are finished and called
     * {@link SingleThreadedEventWriter#onThreadComplete()}.
     * <p>
     * It waits for all {@link SingleThreadedEventWriter} to finish, and then it's called.
     */
     void onComplete();
}
