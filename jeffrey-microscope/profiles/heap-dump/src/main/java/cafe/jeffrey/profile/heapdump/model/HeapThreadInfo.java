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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Thread information extracted from a heap dump.
 *
 * <p>The optional fields ({@code frameCount}, {@code localsCount},
 * {@code localsBytes}, {@code state}) are populated by joining the thread's
 * {@code thread_serial} to the {@code stack_trace_frame} / {@code stack_frame}
 * tables and to {@code ROOT_JAVA_FRAME} entries in {@code gc_root}. They are
 * {@code null} when no STACK_TRACE record exists for the thread.
 *
 * @param objectId     the object ID of the Thread instance in the heap
 * @param name         thread name
 * @param daemon       whether the thread is a daemon thread
 * @param priority     thread priority
 * @param retainedSize retained heap size in bytes (null if not calculated - expensive operation)
 * @param frameCount   number of stack frames at dump time (null when stack absent)
 * @param localsCount  number of locals referenced from any frame (null when stack absent)
 * @param localsBytes  sum of shallow sizes across all frame-local references in bytes
 * @param state        heuristic thread state derived from the top frame;
 *                     null when stack absent
 */
public record HeapThreadInfo(
        long objectId,
        String name,
        boolean daemon,
        int priority,
        Long retainedSize,
        Integer frameCount,
        Integer localsCount,
        Long localsBytes,
        HeapThreadState state
) {
}
