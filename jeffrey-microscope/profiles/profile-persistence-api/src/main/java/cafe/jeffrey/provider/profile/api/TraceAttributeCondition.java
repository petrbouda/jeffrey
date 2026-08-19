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

package cafe.jeffrey.provider.profile.api;

import java.util.Objects;

/**
 * One condition of an attribute search: a key, how to compare it, and what to compare it against.
 *
 * @param key      which key the condition is about
 * @param operator how the value is compared
 * @param value    what to compare against; ignored — and permitted to be {@code null} — for
 *                 {@link TraceAttributeOperator#EXISTS}
 */
public record TraceAttributeCondition(
        TraceAttributeKeyId key,
        TraceAttributeOperator operator,
        String value) {

    public TraceAttributeCondition {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        if (operator.needsValue() && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("operator " + operator + " needs a value: " + key.key());
        }
        if (operator.isNumeric() && !isNumber(value)) {
            throw new IllegalArgumentException(
                    "operator " + operator + " needs a numeric value, got: " + value);
        }
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
