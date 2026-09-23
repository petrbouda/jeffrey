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
