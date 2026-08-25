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

import java.util.List;
import java.util.Map;

/**
 * A whole trace: its summary, its spans in draw order, and the instants that happened inside it.
 *
 * @param trace         the trace's own row, so the detail view can render a header without a second
 *                      request
 * @param spans         every span, pre-ordered depth-first with {@code depth} set
 * @param notifications what the application said while the trace ran, oldest first. Sent with the
 *                      trace rather than fetched separately: a trace carries few of them, and both
 *                      readings the UI needs — a flat rail and a per-span grouping — come off the
 *                      one list
 * @param exceptions    every throw recorded inside the trace, oldest first, each already attributed
 *                      to the innermost span open on its thread
 * @param eventFields   how the recording described the fields of the event types these spans came
 *                      from, keyed by event type. Sent once per trace rather than per span, since
 *                      every span of a type shares its schema. It is what lets the span panel show a
 *                      field by its recorded label and format it by its recorded content type,
 *                      without knowing anything about any particular event type
 */
public record TraceDetail(
        TraceRow trace,
        List<TraceSpanRow> spans,
        List<TraceNotificationRow> notifications,
        List<TraceExceptionRow> exceptions,
        Map<String, List<EventFieldRow>> eventFields) {
}
