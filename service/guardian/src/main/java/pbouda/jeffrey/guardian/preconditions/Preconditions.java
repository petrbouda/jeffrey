/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.guardian.preconditions;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.profile.summary.event.EventSummary;

import java.util.List;

public record Preconditions(
        Boolean debugSymbolsAvailable,
        Boolean kernelSymbolsAvailable,
        List<EventSummary> eventTypes,
        EventSource eventSource,
        GarbageCollectorType garbageCollectorType,
        GraphType graphType) {

    public static final Preconditions EMPTY = new PreconditionsBuilder().build();

    public static PreconditionsBuilder builder() {
        return new PreconditionsBuilder();
    }

    /**
     * Matches the current preconditions with the other one. It's not the same as equals,
     * because it doesn't compare the object's values. It compares the values of the fields
     * based on the business logic.
     *
     * @param other the other preconditions to match with the current one.
     * @return true if the preconditions match based on the business logic, false otherwise.
     */
    public boolean matches(Preconditions other) {
        return match(this.debugSymbolsAvailable, other.debugSymbolsAvailable)
                && match(this.kernelSymbolsAvailable, other.kernelSymbolsAvailable)
                && match(this.eventSource, other.eventSource)
                && match(this.garbageCollectorType, other.garbageCollectorType)
                && match(this.graphType, other.graphType)
                && containsEvents(this.eventTypes, other.eventTypes);
    }

    private static boolean match(Object current, Object other) {
        return current == null || current.equals(other);
    }

    private static boolean containsEvents(List<EventSummary> current, List<EventSummary> other) {
        return current == null || current.isEmpty() || other.containsAll(current);
    }
}
