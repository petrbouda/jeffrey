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

package cafe.jeffrey.hub.core.scheduler.job;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks how many times the head event of a scope has failed to process, so a failing event
 * can be retried a bounded number of times before it is dropped as a poison message. Attempts
 * are kept in memory only: a restart resets the counter, which merely grants a poison event
 * another round of retries — safe, because processing never advances past an event that still
 * has retries left.
 */
public class EventProcessingAttempts {

    private record FailedEvent(long offset, int attempts) {
    }

    private final Map<String, FailedEvent> failuresByScope = new ConcurrentHashMap<>();

    /**
     * Records a processing failure of the event with the given offset and returns the number of
     * failed attempts so far. A failure of a different offset than the currently tracked one
     * starts counting from one again.
     */
    public int recordFailure(String scopeId, long offset) {
        FailedEvent updated = failuresByScope.merge(
                scopeId,
                new FailedEvent(offset, 1),
                (current, initial) -> current.offset() == offset
                        ? new FailedEvent(offset, current.attempts() + 1)
                        : initial);
        return updated.attempts();
    }

    /**
     * Forgets any tracked failure for the given scope, typically after the head event was
     * processed successfully or permanently dropped.
     */
    public void clear(String scopeId) {
        failuresByScope.remove(scopeId);
    }
}
