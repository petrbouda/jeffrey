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
