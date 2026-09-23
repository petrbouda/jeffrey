/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.test;

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
