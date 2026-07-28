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

import java.util.regex.Pattern;

/**
 * Decides which threads belong on the same lane.
 *
 * <p>Pools name their workers one of two ways. Some give every worker the same name — a recording
 * can hold 351 threads all called {@code oracleApp:connection-adder} — and those group on the name
 * itself. Others number them, as in {@code http-nio-8080-exec-17}, and grouping those means dropping
 * the number.
 *
 * <p>Only the <em>trailing</em> number is dropped, which matters for names like
 * {@code pool-3-thread-1}: the pool's own number identifies which executor the thread belongs to, so
 * collapsing {@code pool-3} and {@code pool-4} together would merge two unrelated pools. Threads
 * whose names carry no trailing number are their own group of one, and a group of one renders
 * exactly as a plain thread does today.
 */
public final class ThreadGroupKey {

    /**
     * A trailing worker number. Anchored to the end so only the last number in the name goes, and
     * preceded by a non-digit so a name made entirely of digits keeps something to group under. The
     * separator that introduces the number is left in place, which keeps {@code http-nio-8080-exec-*}
     * readable as the pool it names.
     */
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(?<=[^\\d])\\d+$");

    /**
     * Stands in for the number that was dropped, so a grouped name reads as a pattern rather than as
     * one particular worker.
     */
    private static final String WILDCARD = "*";

    private static final String UNNAMED = "<unnamed>";

    private ThreadGroupKey() {
    }

    /**
     * The name threads are grouped under. Equal keys mean one lane.
     */
    public static String of(String threadName) {
        if (threadName == null || threadName.isBlank()) {
            return UNNAMED;
        }
        return TRAILING_NUMBER.matcher(threadName).replaceAll(WILDCARD);
    }
}
