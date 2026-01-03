/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.profile;

import javax.sql.DataSource;

public interface EventWriter {

    /**
     * Factory for creating EventWriter instances for a profile database.
     */
    @FunctionalInterface
    interface Factory {
        EventWriter create(DataSource dataSource);
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
