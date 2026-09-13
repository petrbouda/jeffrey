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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;

import java.time.Clock;
import java.util.function.Function;

/**
 * Activity services built the way a test outside this package cannot build them: the constructors
 * that take an executor are package-private, and a full retained-slot table is only reachable with
 * scans that never finish.
 */
public final class ActivityServiceFixtures {

    private ActivityServiceFixtures() {
    }

    /**
     * A service that admits scans and never runs them, so every one it admits stays queued and holds
     * its retained slot for good. The sixteen-scan table fills on the sixteenth {@code start}.
     */
    public static HubActivityService neverRunning(
            Function<ActivityRequest, ReplayStreamSubscription> source, Clock clock) {
        return new HubActivityService(source, _ -> { }, clock);
    }
}
