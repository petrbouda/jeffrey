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
package cafe.jeffrey.profile.mcp;

import tools.jackson.databind.node.ObjectNode;

/**
 * A parameter's {@link ToolParamBounds}, checked and ready to be written into its schema.
 * <p>
 * Each element is null when it was not declared. An integer parameter carries whole numbers only and
 * writes them as JSON integers, so a client reading {@code "maximum": 50} for an {@code integer} is not
 * handed {@code 50.0}. An exclusive bound is written under {@code exclusiveMinimum} or
 * {@code exclusiveMaximum} in place of the inclusive keyword, as JSON Schema 2020-12 has it.
 */
record ParamBounds(
        Double defaultValue, Double min, Double max, boolean exclusiveMin, boolean exclusiveMax, boolean integral) {

    private static final String SCHEMA_DEFAULT = "default";
    private static final String SCHEMA_MINIMUM = "minimum";
    private static final String SCHEMA_MAXIMUM = "maximum";
    private static final String SCHEMA_EXCLUSIVE_MINIMUM = "exclusiveMinimum";
    private static final String SCHEMA_EXCLUSIVE_MAXIMUM = "exclusiveMaximum";

    ParamBounds {
        requireFitting(defaultValue, integral, SCHEMA_DEFAULT);
        requireFitting(min, integral, SCHEMA_MINIMUM);
        requireFitting(max, integral, SCHEMA_MAXIMUM);
        if (exclusiveMin && min == null) {
            throw new IllegalArgumentException("exclusiveMin is set but no minimum is declared");
        }
        if (exclusiveMax && max == null) {
            throw new IllegalArgumentException("exclusiveMax is set but no maximum is declared");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("minimum " + min + " is above maximum " + max);
        }
        if (min != null && max != null && min.equals(max) && (exclusiveMin || exclusiveMax)) {
            throw new IllegalArgumentException("the range " + min + " to " + max + " holds no value once a bound "
                    + "is exclusive");
        }
        if (defaultValue != null && min != null && (exclusiveMin ? defaultValue <= min : defaultValue < min)) {
            throw new IllegalArgumentException("default " + defaultValue + " is outside minimum " + min);
        }
        if (defaultValue != null && max != null && (exclusiveMax ? defaultValue >= max : defaultValue > max)) {
            throw new IllegalArgumentException("default " + defaultValue + " is outside maximum " + max);
        }
    }

    /**
     * @param integral whether the parameter is an integer type, whose bounds must be whole numbers
     */
    static ParamBounds of(ToolParamBounds declared, boolean integral) {
        return new ParamBounds(
                declaredOrNull(declared.defaultValue()),
                declaredOrNull(declared.min()),
                declaredOrNull(declared.max()),
                declared.exclusiveMin(),
                declared.exclusiveMax(),
                integral);
    }

    void writeTo(ObjectNode property) {
        write(property, SCHEMA_DEFAULT, defaultValue);
        write(property, exclusiveMin ? SCHEMA_EXCLUSIVE_MINIMUM : SCHEMA_MINIMUM, min);
        write(property, exclusiveMax ? SCHEMA_EXCLUSIVE_MAXIMUM : SCHEMA_MAXIMUM, max);
    }

    private void write(ObjectNode property, String keyword, Double value) {
        if (value == null) {
            return;
        }
        if (integral) {
            property.put(keyword, value.longValue());
        } else {
            property.put(keyword, value.doubleValue());
        }
    }

    private static Double declaredOrNull(double value) {
        return Double.isNaN(value) ? null : value;
    }

    private static void requireFitting(Double value, boolean integral, String keyword) {
        if (value == null) {
            return;
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException(keyword + " must be finite");
        }
        if (integral && value != Math.rint(value)) {
            throw new IllegalArgumentException(keyword + " " + value + " is not a whole number");
        }
    }
}
