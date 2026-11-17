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
