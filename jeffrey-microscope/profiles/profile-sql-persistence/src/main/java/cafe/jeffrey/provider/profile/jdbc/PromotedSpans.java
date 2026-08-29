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

package cafe.jeffrey.provider.profile.jdbc;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every JDK event type the derivation turns into a span, whichever promotion did it.
 * <p>
 * The two promotions are described apart because they behave differently — {@link BlockingLeafSpans}
 * gives fixed names to leaves, {@link MethodSpans} gives an event its own name and lets it nest —
 * but the queries that have to <em>exclude</em> a promoted event care about none of that. They only
 * need to know that the event is already drawn as a bar, so this is the union they ask.
 * <p>
 * Both exclusions exist for the same reason: an event promoted into a span must not also be counted
 * or listed as a loose event under the very span it became a child of, or the reader sees it twice
 * and the totals say the same microsecond twice.
 */
final class PromotedSpans {

    /** The promoted event types, for membership tests in the queries that must leave them out. */
    static final Set<String> EVENT_TYPES = Stream.concat(
                    BlockingLeafSpans.EVENT_TYPES.stream(),
                    Stream.of(MethodSpans.EVENT_TYPE))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * The promoted event types as a quoted SQL list, for the one statement built as a constant rather
     * than bound per call. Values come from {@code EventTypeName} constants, never from input.
     */
    static String sqlQuotedEventTypes() {
        return EVENT_TYPES.stream()
                .sorted()
                .map(eventType -> "'" + eventType + "'")
                .collect(Collectors.joining(", "));
    }

    private PromotedSpans() {
    }
}
