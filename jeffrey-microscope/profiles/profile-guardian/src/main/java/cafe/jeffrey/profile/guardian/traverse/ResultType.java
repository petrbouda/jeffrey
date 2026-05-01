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

package cafe.jeffrey.profile.guardian.traverse;

public enum ResultType {
    /**
     * Accumulates {@code Frame.totalSamples()} of each matched subtree root.
     * Attributes the ENTIRE subtree (including callees) to the guard. Correct when
     * the matched frame itself performs the work (regex engine, logback formatter,
     * StringBuilder internals).
     */
    SAMPLES,

    /**
     * As {@link #SAMPLES} but uses {@code Frame.totalWeight()} (bytes for allocations,
     * duration for blocking).
     */
    WEIGHT,

    /**
     * Accumulates self-time ({@code Frame.selfSamples()}) of frames whose name matches
     * the guard's base matcher, recursively walking into children only while they too
     * match the matcher. Stops descending as soon as the call stack leaves the matcher's
     * namespace.
     * <p>
     * Use for guards whose matched frame is a <em>pass-through wrapper</em> that immediately
     * dispatches to user code — e.g. {@code Method.invoke} / {@code DirectMethodHandleAccessor}
     * for reflection, {@code ObjectInputStream#readObject} for serialization. The wrapper
     * subtree contains the reflected/deserialized target's own CPU; attributing it to the
     * wrapper is misleading.
     */
    SELF_SAMPLES,

    /** Self-weight variant of {@link #SELF_SAMPLES}. */
    SELF_WEIGHT
}
