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

/**
 * The events that ran inside one span's window, and whether the window held more than the row cap
 * allowed. The flag reaches the UI so a busy span's drill-down can say "showing the first N events"
 * instead of presenting a truncated list as the whole window.
 *
 * @param events    what ran on the span's thread while it was open, ordered by start time
 * @param truncated whether the window held more events than the cap allowed
 */
public record TraceSpanEvents(List<TraceEventRow> events, boolean truncated) {

    public static final TraceSpanEvents EMPTY = new TraceSpanEvents(List.of(), false);
}
