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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.shared.common.model.ThreadInfo;

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
