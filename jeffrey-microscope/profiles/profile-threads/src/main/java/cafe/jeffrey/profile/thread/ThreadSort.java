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

    EVENT_COUNT(Comparator.comparingLong(ThreadRow::eventsCount).reversed()),
    LIFESPAN(Comparator.comparingLong(ThreadRow::totalDuration).reversed()),
    NAME(Comparator.comparing(row -> row.threadInfo().name(), Comparator.nullsLast(String::compareTo)));

    /**
     * Threads that tie on the chosen key are broken by name, so paging stays stable: without a total
     * order, two requests for the same page could return different threads.
     */
    private static final Comparator<ThreadRow> TIE_BREAK =
            Comparator.comparing((ThreadRow row) -> row.threadInfo().name(), Comparator.nullsLast(String::compareTo))
                    .thenComparingLong(row -> row.threadInfo().javaId())
                    .thenComparingLong(row -> row.threadInfo().osId());

    private final Comparator<ThreadRow> comparator;

    ThreadSort(Comparator<ThreadRow> comparator) {
        this.comparator = comparator;
    }

    public Comparator<ThreadRow> comparator() {
        return comparator.thenComparing(TIE_BREAK);
    }
}
