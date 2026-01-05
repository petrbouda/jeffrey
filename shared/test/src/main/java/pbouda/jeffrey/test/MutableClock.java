/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A mutable clock that can be advanced for testing time-dependent code.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initial) {
        this(initial, ZoneId.systemDefault());
    }

    public MutableClock(Instant initial, ZoneId zone) {
        this.currentInstant = new AtomicReference<>(initial);
        this.zone = zone;
    }

    public void advance(Duration duration) {
        currentInstant.updateAndGet(instant -> instant.plus(duration));
    }

    public void set(Instant instant) {
        currentInstant.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(currentInstant.get(), zone);
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }
}
