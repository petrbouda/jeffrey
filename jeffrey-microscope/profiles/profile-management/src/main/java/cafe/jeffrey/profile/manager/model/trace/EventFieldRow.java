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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * How the recording described one field of an event type, for the UI to render a span's event
 * fields by.
 * <p>
 * The event type is not repeated here: these arrive grouped by it in
 * {@link TraceDetail#eventFields()}.
 *
 * @param field       the field's name as it appears in the event's JSON, e.g. {@code rows}
 * @param label       its recorded label, e.g. "Affected/Returned Rows"
 * @param description its recorded description, or {@code null} when it carries none
 * @param contentType the JFR content-type annotation that says how to format the value —
 *                    {@code jdk.jfr.DataAmount}, {@code jdk.jfr.Timespan} and so on — or
 *                    {@code null} for a value that is just itself
 */
public record EventFieldRow(
        String field,
        String label,
        String description,
        String contentType) {
}
