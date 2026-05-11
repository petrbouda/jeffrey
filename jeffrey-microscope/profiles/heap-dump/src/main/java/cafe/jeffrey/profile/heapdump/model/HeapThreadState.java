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
