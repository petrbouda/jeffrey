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

package cafe.jeffrey.profile.manager.model.span;

/**
 * One JFR event that ran on a span's thread within its window, for the span events drill-down.
 * The UI groups these by {@code eventType} and lays them out on a timeline.
 */
public record SpanEventRow(
        String eventType,
        long startEpochMillis,
        long durationNanos,
        String fields) {
}
