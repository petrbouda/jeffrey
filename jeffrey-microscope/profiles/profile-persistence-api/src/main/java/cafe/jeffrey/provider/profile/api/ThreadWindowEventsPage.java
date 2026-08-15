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
