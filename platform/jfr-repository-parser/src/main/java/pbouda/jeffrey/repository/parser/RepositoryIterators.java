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

package pbouda.jeffrey.repository.parser;

import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

/**
 * Factory methods for creating repository iterators.
 * Automatically selects between single and parallel processing based on the number of sessions.
 */
public abstract class RepositoryIterators {

    /**
     * Automatically decides the best way to iterate over the sessions. If there is only one repository,
     * it will use single processing. If there are multiple sessions, it will process them in parallel.
     *
     * @param sessions          paths to all JFR sessions
     * @param processorSupplier creates a processor to collect events and transform them into an output
     * @param timeRange         time range for filtering events
     * @param clock             clock for determining current time (for testability)
     * @param <PARTIAL>         result of processing a single repository
     * @param <RESULT>          collected result of all sessions
     * @return the repository iterator
     */
    public static <PARTIAL, RESULT> RepositoryIterator<PARTIAL, RESULT> automatic(
            List<RecordingSession> sessions,
            Function<RecordingSession, ? extends EventProcessor<PARTIAL>> processorSupplier,
            AbsoluteTimeRange timeRange,
            Clock clock) {

        if (sessions.size() > 1) {
            return parallel(sessions, processorSupplier, timeRange, clock);
        } else if (sessions.size() == 1) {
            RecordingSession session = sessions.getFirst();
            return single(session, processorSupplier.apply(session), timeRange, clock);
        } else {
            throw new IllegalArgumentException("At least one repository path is required");
        }
    }

    /**
     * Iterates over a single repository and applies the processor on each event.
     *
     * @param recordingSession path to the JFR repository
     * @param processor        a processor to collect events and transform them into an output
     * @param timeRange        time range for filtering events
     * @param clock            clock for determining current time (for testability)
     * @param <PARTIAL>        result of processing the repository
     * @param <RESULT>         collected result
     * @return the repository iterator
     */
    public static <PARTIAL, RESULT> RepositoryIterator<PARTIAL, RESULT> single(
            RecordingSession recordingSession,
            EventProcessor<PARTIAL> processor,
            AbsoluteTimeRange timeRange,
            Clock clock) {

        return new JdkRepositoryIterator<>(recordingSession, processor, timeRange, clock);
    }

    /**
     * Iterates over multiple sessions in parallel and applies the processor on each event.
     *
     * @param sessions          paths to all JFR sessions
     * @param processorSupplier creates a processor to collect events and transform them into an output
     * @param timeRange         time range for filtering events
     * @param clock             clock for determining current time (for testability)
     * @param <PARTIAL>         result of processing a single repository
     * @param <RESULT>          collected result of all sessions
     * @return the repository iterator
     */
    public static <PARTIAL, RESULT> RepositoryIterator<PARTIAL, RESULT> parallel(
            List<RecordingSession> sessions,
            Function<RecordingSession, ? extends EventProcessor<PARTIAL>> processorSupplier,
            AbsoluteTimeRange timeRange,
            Clock clock) {

        Function<RecordingSession, RepositoryIterator<PARTIAL, PARTIAL>> singleIterator =
                session -> new JdkRepositoryIterator<>(
                        session, processorSupplier.apply(session), timeRange, clock);
        return new ParallelRepositoryIterator<>(sessions, singleIterator);
    }
}
