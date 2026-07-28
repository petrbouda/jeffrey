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

package cafe.jeffrey.profile.thread;

import java.util.Comparator;

/**
 * The order the timeline hands threads out in. Ordering belongs on this side now that only the first
 * page is sent: whichever threads sort first are the ones that arrive, so "busiest" has to mean the
 * same thing here as it did when the page sorted the whole set itself.
 */
public enum ThreadSort {

    EVENT_COUNT(
            Comparator.comparingLong(ThreadRow::eventsCount).reversed(),
            Comparator.comparingLong(ThreadGroupMembers::eventsCount).reversed()),
    LIFESPAN(
            Comparator.comparingLong(ThreadRow::totalDuration).reversed(),
            Comparator.comparingLong(ThreadGroupMembers::totalDuration).reversed()),
    NAME(
            Comparator.comparing(row -> row.threadInfo().name(), Comparator.nullsLast(String::compareTo)),
            Comparator.comparing(ThreadGroupMembers::key));

    /**
     * Threads that tie on the chosen key are broken by name, so paging stays stable: without a total
     * order, two requests for the same page could return different threads.
     */
    private static final Comparator<ThreadRow> TIE_BREAK =
            Comparator.comparing((ThreadRow row) -> row.threadInfo().name(), Comparator.nullsLast(String::compareTo))
                    .thenComparingLong(row -> row.threadInfo().javaId())
                    .thenComparingLong(row -> row.threadInfo().osId());

    /**
     * Groups tie-break on their key, which is unique among groups by construction.
     */
    private static final Comparator<ThreadGroupMembers> GROUP_TIE_BREAK =
            Comparator.comparing(ThreadGroupMembers::key);

    private final Comparator<ThreadRow> comparator;
    private final Comparator<ThreadGroupMembers> groupComparator;

    ThreadSort(Comparator<ThreadRow> comparator, Comparator<ThreadGroupMembers> groupComparator) {
        this.comparator = comparator;
        this.groupComparator = groupComparator;
    }

    /**
     * Orders individual threads — the members behind one collapsed lane.
     */
    public Comparator<ThreadRow> comparator() {
        return comparator.thenComparing(TIE_BREAK);
    }

    /**
     * Orders the lanes themselves. A group's key is the sum of its members for a count, and the
     * longest-lived member for a lifespan, so "busiest" means the same thing at both levels.
     */
    public Comparator<ThreadGroupMembers> groupComparator() {
        return groupComparator.thenComparing(GROUP_TIE_BREAK);
    }
}
