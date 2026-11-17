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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.microscope.model.ThreadInfo;

import java.time.Duration;

/**
 * The bare minimum the timeline needs from one event: who, when, how long, and which band it belongs
 * to. Everything the tooltip shows is left in the database and fetched per band on demand, so the
 * timeline query can skip the event's JSON fields entirely.
 *
 * @param duration length of the event, or {@code null} for the instantaneous thread start/end events
 */
public record ThreadTimelineEvent(
        ThreadInfo threadInfo,
        Duration start,
        Duration duration,
        ThreadState state) {
}
