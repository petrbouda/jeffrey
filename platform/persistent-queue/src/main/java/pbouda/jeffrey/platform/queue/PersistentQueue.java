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

package pbouda.jeffrey.platform.queue;

import java.util.List;

/**
 * A persistent event queue backed by durable storage. Events are appended
 * with auto-generated sequential offsets. Multiple independent consumers
 * can poll and acknowledge events at their own pace.
 *
 * <p>The queue is scope-independent: each operation takes a {@code scopeId}
 * parameter, allowing a single queue instance to serve multiple logical
 * partitions (e.g. different workspaces) within the same storage backend.
 *
 * @param <T> the type of the event payload
 */
public interface PersistentQueue<T> {

    /**
     * Appends a single event to the queue within the given scope.
     *
     * @param scopeId the scope identifier (e.g. workspace ID)
     * @param event   the event to append
     */
    void append(String scopeId, T event);

    /**
     * Appends multiple events to the queue in a single batch operation
     * within the given scope.
     *
     * @param scopeId the scope identifier (e.g. workspace ID)
     * @param events  the events to append
     */
    void appendBatch(String scopeId, List<T> events);

    /**
     * Polls for unprocessed events for the given consumer within the given scope.
     * Returns all events that have an offset greater than the consumer's last
     * acknowledged offset. If the consumer does not exist yet, it is automatically
     * registered.
     *
     * @param scopeId    the scope identifier (e.g. workspace ID)
     * @param consumerId the unique identifier of the consumer
     * @return list of unprocessed queue entries, ordered by offset
     */
    List<QueueEntry<T>> poll(String scopeId, String consumerId);

    /**
     * Acknowledges that a consumer has processed events up to (and including)
     * the given offset within the given scope. Subsequent polls will only return
     * events after this offset.
     *
     * @param scopeId    the scope identifier (e.g. workspace ID)
     * @param consumerId the unique identifier of the consumer
     * @param offset     the offset of the last successfully processed event
     */
    void acknowledge(String scopeId, String consumerId, long offset);

    /**
     * Returns all events in the queue for the given scope, regardless of consumer
     * state. Useful for display, debugging, or administrative purposes.
     *
     * @param scopeId the scope identifier (e.g. workspace ID)
     * @return list of all queue entries, ordered by offset descending
     */
    List<QueueEntry<T>> findAll(String scopeId);

    /**
     * Removes events that have been acknowledged by ALL consumers for the given scope.
     * Events with offset &lt;= minimum consumer offset are safe to delete.
     *
     * @param scopeId the scope identifier (e.g. workspace ID)
     * @return number of events removed
     */
    int compact(String scopeId);
}
