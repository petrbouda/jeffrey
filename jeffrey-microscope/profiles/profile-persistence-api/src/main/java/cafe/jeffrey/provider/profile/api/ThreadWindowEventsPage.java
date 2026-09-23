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

import java.util.List;

/**
 * One page of a thread-window drill-down: the events that fit under the row cap, and whether the
 * window held more. The flag is what lets the UI say "showing the first N events" instead of
 * silently presenting a truncated list as the whole of the window.
 *
 * @param events    the events that fit under the cap, ordered by start time
 * @param truncated whether the window held more events than the cap allowed
 */
public record ThreadWindowEventsPage(List<ThreadWindowEventRecord> events, boolean truncated) {
}
