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

package cafe.jeffrey.profile.advisor.run;

/**
 * The state of a whole batch run — one launch that processes every analyzable event type for a profile.
 * Derived from the per-type runs it aggregates: {@link #QUEUED} until any type starts, {@link #RUNNING}
 * while at least one is in flight, and a terminal {@link #COMPLETED} or {@link #FAILED} once all have
 * settled ({@code FAILED} only when every type failed — a batch where some types produced a report is a
 * completed batch that reports the failures per type).
 */
public enum BatchStatus {

    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
