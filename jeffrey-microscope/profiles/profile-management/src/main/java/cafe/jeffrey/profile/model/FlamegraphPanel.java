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

package cafe.jeffrey.profile.model;

/**
 * A single flamegraph card, fully described server-side so the frontend renders it without inferring
 * anything from the event code. The backend owns which panels a profile shows and how each is presented;
 * this is the unit of that ordered grid.
 *
 * <p>Flamegraph <em>generation</em> is unchanged — it still runs on the code-keyed generate endpoint —
 * so this descriptor carries display + option-default metadata only. The embedded {@link EventSummaryResult}
 * supplies the card's detail rows (source/subtype/extras/samples/weight) and, in differential mode, the
 * secondary summary for delta badges.
 *
 * @param section        logical section id (e.g. "execution", "allocation", "blocking")
 * @param order          display order within the grid
 * @param title          card title (JFR: curated section title; pprof/OTLP: the event code verbatim)
 * @param color          card accent color token ("blue"|"purple"|"green"|"pink"|"red")
 * @param icon           bootstrap icon class (e.g. "bi-sprint")
 * @param showType       whether the card shows the raw event-type "Type" detail row (JFR only)
 * @param threadMode     the "Use Thread-mode" toggle
 * @param weight         the "Use weight" toggle (+ label and formatting kind)
 * @param excludeNonJava the "Exclude non-Java Samples" toggle
 * @param excludeIdle    the "Exclude Idle Samples" toggle
 * @param onlyUnsafe     the "Only Allocations with Unsafe" toggle
 * @param classification presentation-role flags for route-based show/hide
 * @param event          the underlying event summary (detail rows + samples + secondary for deltas)
 */
public record FlamegraphPanel(
        String section,
        int order,
        String title,
        String color,
        String icon,
        boolean showType,
        ToggleOption threadMode,
        WeightOption weight,
        ToggleOption excludeNonJava,
        ToggleOption excludeIdle,
        ToggleOption onlyUnsafe,
        Classification classification,
        EventSummaryResult event) {

    public FlamegraphPanel {
        if (section == null || title == null || color == null || icon == null || event == null) {
            throw new IllegalArgumentException("FlamegraphPanel requires non-null section/title/color/icon/event");
        }
        if (order < 0) {
            throw new IllegalArgumentException("FlamegraphPanel.order must be >= 0: " + order);
        }
    }
}
