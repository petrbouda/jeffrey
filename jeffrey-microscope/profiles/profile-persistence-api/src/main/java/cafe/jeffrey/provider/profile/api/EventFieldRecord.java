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

/**
 * One declared field of an event type, as the recording described it.
 * <p>
 * This is what makes a span's event fields readable without the UI knowing anything about any
 * particular event: JFR records a label, a description and a content type for every field, and the
 * parser stores them alongside the event type. A renderer that reads these can show
 * {@code rows} as "Affected/Returned Rows" and format {@code responseLength} as a byte count
 * without a per-event-type branch — and a newly instrumented event is readable the day it lands.
 *
 * @param eventType   the event type this field belongs to, e.g. {@code jeffrey.JdbcQuery}
 * @param field       the field's name as it appears in the event's JSON, e.g. {@code rows}
 * @param label       its {@code @Label}, e.g. "Affected/Returned Rows"; never null in practice,
 *                    since JFR falls back to the field name
 * @param description its {@code @Description}, or {@code null} when the field carries none
 * @param contentType the JFR content-type annotation that says how to format the value —
 *                    {@code jdk.jfr.DataAmount}, {@code jdk.jfr.Timespan},
 *                    {@code jdk.jfr.Percentage} and so on — or {@code null} for a plain value
 */
public record EventFieldRecord(
        String eventType,
        String field,
        String label,
        String description,
        String contentType) {
}
