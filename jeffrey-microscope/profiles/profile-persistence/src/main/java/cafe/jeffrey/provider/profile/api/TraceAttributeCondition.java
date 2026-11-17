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
