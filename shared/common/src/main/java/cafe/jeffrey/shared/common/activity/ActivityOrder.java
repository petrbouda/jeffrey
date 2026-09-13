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

package cafe.jeffrey.shared.common.activity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** How a poll ranks the buckets it returns. */
public enum ActivityOrder {

    /** Busiest first, by event count. */
    EVENTS("events"),
    /** Greatest variety first, by distinct event types. */
    TYPES("types"),
    /** Chronological. */
    TIME("time");

    private static final Map<String, ActivityOrder> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ActivityOrder::code, Function.identity()));

    private final String code;

    ActivityOrder(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** Accepts the wire spelling; {@code null} selects {@link #EVENTS}. */
    public static ActivityOrder fromCode(String code) {
        if (code == null) {
            return EVENTS;
        }
        ActivityOrder order = BY_CODE.get(code);
        if (order == null) {
            throw new IllegalArgumentException("order must be one of " + BY_CODE.keySet());
        }
        return order;
    }
}
