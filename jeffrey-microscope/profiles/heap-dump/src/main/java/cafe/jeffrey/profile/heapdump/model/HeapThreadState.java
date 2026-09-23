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
 * Approximation of the JVM thread's runtime state, inferred from the top frame
 * of its stack at the moment the heap dump was taken.
 *
 * <p>Distinct from {@link java.lang.Thread.State} because the JVM's enum doesn't
 * tell apart what we want to surface in the UI:
 * <ul>
 *   <li>{@code PARKED} — top frame is {@code Unsafe.park} (the engine under
 *       every LockSupport, AQS lock, BlockingQueue, etc.).</li>
 *   <li>{@code WAITING} — top frame is {@code Object.wait} (classic monitor
 *       wait/notify).</li>
 *   <li>{@code SLEEPING} — top frame is {@code Thread.sleep*}.</li>
 *   <li>{@code NATIVE} — top frame is a native method that doesn't match the
 *       known blocking primitives (typically an I/O syscall).</li>
 *   <li>{@code RUNNABLE} — anything else; the thread was executing Java code.</li>
 * </ul>
 *
 * <p>Serialised by name (Jackson default) so the frontend receives the
 * uppercase label directly.
 */
public enum HeapThreadState {
    PARKED,
    WAITING,
    SLEEPING,
    NATIVE,
    RUNNABLE
}
