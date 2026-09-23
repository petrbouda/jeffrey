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
