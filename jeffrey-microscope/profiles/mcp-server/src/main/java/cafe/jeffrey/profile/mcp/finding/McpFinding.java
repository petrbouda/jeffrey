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

package cafe.jeffrey.profile.mcp.finding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One judgement a tool made about a profile, in the shape every judging tool shares.
 * <p>
 * Most of the MCP surface reports figures and routes — a dashboard, an export, a "where to go next".
 * A few tools go further and say that something is wrong: the auto-analysis rule set, the container
 * throttling verdict. Before this record each of them said so in its own shape, and a client reading
 * three of them had to notice for itself that they described one condition. Now they all emit this,
 * and {@link McpFindings#merge} can fold them together.
 * <p>
 * The fields are the ones a reader needs to check the claim rather than take it on trust:
 * {@code source} names the tool that made it, {@code evidence} carries the figures it rests on, and
 * {@code nextTool} names the call that shows the figures in full. The {@code id} is stable across
 * tools — {@link McpFindings#id} builds it from the category and the subject — which is what lets two
 * tools reporting the same condition collapse into one finding, the more severe one surviving.
 *
 * @param id       stable identity, {@code category:subject}, for de-duplication across tools
 * @param severity how much it matters; {@link Severity#OK} is a check that ran and passed
 * @param category the subsystem — a JMC rule topic such as {@code garbage_collection}, or a family
 * @param title    the one-line statement
 * @param detail   the explanation behind it, or null
 * @param source   the tool that produced it, or null
 * @param evidence the figures it rests on, in the order they were added
 * @param action   what the source suggests doing about it, or null — a suggestion, not a diagnosis
 * @param nextTool the call that carries the figures behind the finding in full, or null
 */
public record McpFinding(
        String id,
        Severity severity,
        String category,
        String title,
        String detail,
        String source,
        Map<String, Object> evidence,
        String action,
        String nextTool) {

    /**
     * Ordered from the most to the least severe, so the ordinal is the sort key and the smaller one
     * wins a merge.
     */
    public enum Severity {
        CRITICAL,
        WARNING,
        INFO,
        /** A check that ran and found nothing wrong — evidence in its own right, not a non-event. */
        OK;

        public boolean outranks(Severity other) {
            return ordinal() < other.ordinal();
        }
    }

    public McpFinding {
        requireText(id, "id");
        requireText(category, "category");
        requireText(title, "title");
        Objects.requireNonNull(severity, "severity is required");
        // Insertion order is kept on purpose: a builder adds the headline figure first, and a reader
        // sees the evidence in the order the source thought mattered.
        evidence = evidence == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /**
     * Starts a finding about one subject in one category. The subject is what the id is built from,
     * so two tools describing the same condition have to agree on it — {@code gc:pauses}, not
     * {@code gc:long-pauses} in one and {@code gc:pause-time} in the other.
     */
    public static Builder of(String category, String subject) {
        return new Builder(category, subject);
    }

    public static final class Builder {

        private final String category;
        private final String subject;
        private Severity severity = Severity.INFO;
        private String title;
        private String detail;
        private String source;
        private final Map<String, Object> evidence = new LinkedHashMap<>();
        private String action;
        private String nextTool;

        private Builder(String category, String subject) {
            this.category = category;
            this.subject = subject;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder critical() {
            return severity(Severity.CRITICAL);
        }

        public Builder warning() {
            return severity(Severity.WARNING);
        }

        public Builder info() {
            return severity(Severity.INFO);
        }

        public Builder ok() {
            return severity(Severity.OK);
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * A figure the finding rests on. A null value is skipped rather than written, so a source can
         * add every figure it has without checking which ones this recording carries.
         */
        public Builder evidence(String key, Object value) {
            if (value != null) {
                evidence.put(key, value);
            }
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder nextTool(String nextTool) {
            this.nextTool = nextTool;
            return this;
        }

        public McpFinding build() {
            return new McpFinding(
                    McpFindings.id(category, subject),
                    severity,
                    category,
                    title,
                    detail,
                    source,
                    evidence,
                    action,
                    nextTool);
        }
    }
}
