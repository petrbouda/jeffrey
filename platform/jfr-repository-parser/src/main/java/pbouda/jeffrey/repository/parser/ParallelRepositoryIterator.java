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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Parallel iterator for processing multiple JFR sessions concurrently.
 * Uses a cached thread pool to process each repository in parallel and
 * combines the results using the provided collector.
 *
 * @param <PARTIAL> result of processing a single repository
 * @param <RESULT>  collected result of all sessions
 */
public class ParallelRepositoryIterator<PARTIAL, RESULT> implements RepositoryIterator<PARTIAL, RESULT> {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelRepositoryIterator.class);

    private final List<RecordingSession> sessions;
    private final Function<RecordingSession, RepositoryIterator<PARTIAL, PARTIAL>> singleIteratorFactory;

    /**
     * Creates a parallel iterator for multiple sessions.
     *
     * @param sessions          list of repository paths to process
     * @param singleIteratorFactory factory function to create an iterator for each repository
     */
    public ParallelRepositoryIterator(
            List<RecordingSession> sessions,
            Function<RecordingSession, RepositoryIterator<PARTIAL, PARTIAL>> singleIteratorFactory) {

        this.sessions = sessions;
        this.singleIteratorFactory = singleIteratorFactory;
    }

    @Override
    public RESULT collect(Collector<PARTIAL, RESULT> collector) {
        List<PARTIAL> partials = iterate(collector);
        PARTIAL combined = partialCombination(partials, collector);
        return collector.finisher(combined);
    }

    @Override
    public PARTIAL partialCollect(Collector<PARTIAL, ?> collector) {
        List<PARTIAL> partials = iterate(collector);
        return partialCombination(partials, collector);
    }

    private List<PARTIAL> iterate(Collector<PARTIAL, ?> collector) {
        List<CompletableFuture<PARTIAL>> futures = sessions.stream()
                .map(repository -> asyncExecution(repository, collector))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private CompletableFuture<PARTIAL> asyncExecution(
            RecordingSession session,
            Collector<PARTIAL, ?> collector) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return singleIteratorFactory.apply(session).partialCollect(collector);
            } catch (RepositoryNotFoundException e) {
                LOG.warn("Skipping session with missing repository: sessionId={} sessionName={}",
                        e.getSessionId(), e.getSessionName());
                return collector.empty().get();
            }
        }, Schedulers.sharedVirtual());
    }

    private PARTIAL partialCombination(List<PARTIAL> partials, Collector<PARTIAL, ?> collector) {
        if (partials.isEmpty()) {
            return collector.empty().get();
        } else {
            PARTIAL combined = partials.getFirst();
            for (int i = 1; i < partials.size(); i++) {
                combined = collector.combiner(combined, partials.get(i));
            }
            return combined;
        }
    }
}
