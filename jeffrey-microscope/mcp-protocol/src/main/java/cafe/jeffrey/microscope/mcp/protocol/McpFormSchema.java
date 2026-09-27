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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The form an {@link McpFormElicitation} asks a host to show: one flat object of primitive fields.
 * <p>
 * The elicitation specification restricts a requested schema to exactly that — strings (optionally a
 * {@code date-time}), integers, numbers, booleans and single-choice lists of {@code const}/{@code title}
 * pairs, each with an optional default — because a host renders it as a form, not as a JSON editor.
 * The {@link Field} kinds below are the whole vocabulary, so nothing else can be built.
 */
public final class McpFormSchema {

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_TITLE = "title";
    private static final String SCHEMA_DESCRIPTION = "description";
    private static final String SCHEMA_DEFAULT = "default";
    private static final String SCHEMA_FORMAT = "format";
    private static final String SCHEMA_MINIMUM = "minimum";
    private static final String SCHEMA_MAXIMUM = "maximum";
    private static final String SCHEMA_ONE_OF = "oneOf";
    private static final String SCHEMA_CONST = "const";

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String FORMAT_DATE_TIME = "date-time";

    private final ObjectNode schema;

    private McpFormSchema(ObjectNode schema) {
        this.schema = schema;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The schema as {@code requestedSchema} carries it; a fresh copy each time. */
    public ObjectNode toJson() {
        return schema.deepCopy();
    }

    /**
     * What every field has: the property name the answer comes back under, and what the user is shown.
     *
     * @param name        the property name
     * @param title       the field's label, or null to let the host use the name
     * @param description a longer explanation, or null
     */
    public record Label(String name, String title, String description) {

        public Label {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A form field needs a name");
            }
        }

        void writeTo(ObjectNode property) {
            if (title != null) {
                property.put(SCHEMA_TITLE, title);
            }
            if (description != null) {
                property.put(SCHEMA_DESCRIPTION, description);
            }
        }
    }

    /** One field of the form. Each kind writes its own primitive property. */
    public sealed interface Field
            permits StringField, DateTimeField, IntegerField, NumberField, BooleanField, ChoiceField {

        Label label();

        /** Writes the type, its constraints and the default; the label is written by the builder. */
        void describe(ObjectNode property);
    }

    /** Free text. */
    public record StringField(Label label, String defaultValue) implements Field {

        public StringField {
            Objects.requireNonNull(label, "label");
        }

        @Override
        public void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, TYPE_STRING);
            if (defaultValue != null) {
                property.put(SCHEMA_DEFAULT, defaultValue);
            }
        }
    }

    /** A point in time, as an ISO-8601 {@code date-time} string. */
    public record DateTimeField(Label label, Instant defaultValue) implements Field {

        public DateTimeField {
            Objects.requireNonNull(label, "label");
        }

        @Override
        public void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, TYPE_STRING);
            property.put(SCHEMA_FORMAT, FORMAT_DATE_TIME);
            if (defaultValue != null) {
                property.put(SCHEMA_DEFAULT, defaultValue.toString());
            }
        }
    }

    /** A whole number, optionally bounded (inclusive). */
    public record IntegerField(Label label, Long minimum, Long maximum, Long defaultValue) implements Field {

        public IntegerField {
            Objects.requireNonNull(label, "label");
            if (minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalArgumentException("The minimum of '" + label.name() + "' exceeds its maximum");
            }
            if (defaultValue != null && ((minimum != null && defaultValue < minimum)
                    || (maximum != null && defaultValue > maximum))) {
                throw new IllegalArgumentException("The default of '" + label.name() + "' is outside its range");
            }
        }

        @Override
        public void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, TYPE_INTEGER);
            if (minimum != null) {
                property.put(SCHEMA_MINIMUM, minimum);
            }
            if (maximum != null) {
                property.put(SCHEMA_MAXIMUM, maximum);
            }
            if (defaultValue != null) {
                property.put(SCHEMA_DEFAULT, defaultValue);
            }
        }
    }

    /** A finite number, optionally bounded (inclusive). */
    public record NumberField(Label label, Double minimum, Double maximum, Double defaultValue) implements Field {

        public NumberField {
            Objects.requireNonNull(label, "label");
            requireFinite(label, minimum);
            requireFinite(label, maximum);
            requireFinite(label, defaultValue);
            if (minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalArgumentException("The minimum of '" + label.name() + "' exceeds its maximum");
            }
            if (defaultValue != null && ((minimum != null && defaultValue < minimum)
                    || (maximum != null && defaultValue > maximum))) {
                throw new IllegalArgumentException("The default of '" + label.name() + "' is outside its range");
            }
        }

        private static void requireFinite(Label label, Double value) {
            if (value != null && !Double.isFinite(value)) {
                throw new IllegalArgumentException("'" + label.name() + "' declares a number JSON cannot carry");
            }
        }

        @Override
        public void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, TYPE_NUMBER);
            if (minimum != null) {
                property.put(SCHEMA_MINIMUM, minimum);
            }
            if (maximum != null) {
                property.put(SCHEMA_MAXIMUM, maximum);
            }
            if (defaultValue != null) {
                property.put(SCHEMA_DEFAULT, defaultValue);
            }
        }
    }

    /** Yes or no. */
    public record BooleanField(Label label, Boolean defaultValue) implements Field {

        public BooleanField {
            Objects.requireNonNull(label, "label");
        }

        @Override
        public void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, TYPE_BOOLEAN);
            if (defaultValue != null) {
                property.put(SCHEMA_DEFAULT, defaultValue);
            }
        }
    }

    /**
     * One option out of several, each a value and the words the user sees for it.
     *
     * @param value what comes back in the answer
     * @param title what the user is shown
     */
    public record Choice(String value, String title) {

        public Choice {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("A choice needs a value");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("A choice needs a title the user can read");
            }
        }
    }

    /** A single choice, rendered as a titled {@code oneOf} of {@code const} strings. */
    public record ChoiceField(Label label, List<Choice> choices, String defaultValue) implements Field {

        public ChoiceField {
            Objects.requireNonNull(label, "label");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalArgumentException("'" + label.name() + "' offers no choices");
            }
            choices = List.copyOf(choices);
            Set<String> values = new HashSet<>();
            for (Choice choice : choices) {
                if (!values.add(choice.value())) {
                    throw new IllegalArgumentException(
                            "'" + label.name() + "' offers the choice '" + choice.value() + "' twice");
                }
            }
            if (defaultValue != null && !values.contains(defaultValue)) {
                throw new IllegalArgumentException("The default of '" + label.name() + "' is not one of its choices");
            }
        }

        @Override
        public void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, TYPE_STRING);
            ArrayNode oneOf = property.putArray(SCHEMA_ONE_OF);
            for (Choice choice : choices) {
                oneOf.addObject().put(SCHEMA_CONST, choice.value()).put(SCHEMA_TITLE, choice.title());
            }
            if (defaultValue != null) {
                property.put(SCHEMA_DEFAULT, defaultValue);
            }
        }
    }

    /** Collects fields in the order the user sees them. */
    public static final class Builder {

        private final Map<String, Field> fields = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        private Builder() {
        }

        /** A field the user must fill in. */
        public Builder required(Field field) {
            add(field);
            required.add(field.label().name());
            return this;
        }

        /** A field the user may leave empty. */
        public Builder optional(Field field) {
            add(field);
            return this;
        }

        private void add(Field field) {
            Objects.requireNonNull(field, "field");
            String name = field.label().name();
            if (fields.putIfAbsent(name, field) != null) {
                throw new IllegalArgumentException("The form already has a field named '" + name + "'");
            }
        }

        /**
         * @throws IllegalStateException for a form with no fields, which asks the user nothing
         */
        public McpFormSchema build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("A form needs at least one field");
            }
            ObjectNode schema = McpJson.createObject();
            schema.put(SCHEMA_TYPE, TYPE_OBJECT);
            ObjectNode properties = schema.putObject(SCHEMA_PROPERTIES);
            for (Field field : fields.values()) {
                ObjectNode property = properties.putObject(field.label().name());
                field.describe(property);
                field.label().writeTo(property);
            }
            if (!required.isEmpty()) {
                ArrayNode names = schema.putArray(SCHEMA_REQUIRED);
                required.forEach(names::add);
            }
            return new McpFormSchema(schema);
        }
    }
}
