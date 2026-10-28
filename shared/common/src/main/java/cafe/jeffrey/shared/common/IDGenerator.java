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

package cafe.jeffrey.shared.common;

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.factory.standard.TimeOrderedEpochFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public abstract class IDGenerator {

    /** The UUID version {@link #generate()} produces: time-ordered, epoch millis in the top 48 bits. */
    private static final int TIME_ORDERED_VERSION = 7;

    /** The RFC 9562 variant every generated id carries. */
    private static final int STANDARD_VARIANT = 2;

    /** How far the epoch millis lie above the version and counter bits of the most significant half. */
    private static final int TIMESTAMP_SHIFT = 16;

    /**
     * UUID generator described here:
     * <a href="https://github.com/f4b6a3/uuid-creator/wiki/1.7.-UUIDv7#type-2-plus-1">...</a>
     *
     * @return UUID represented as a string
     */
    public static String generate() {
        return UuidCreator.getTimeOrderedEpochPlus1().toString();
    }

    /**
     * The same kind of id, carrying the time {@code clock} reads instead of the system's: what a
     * session directory is named, so the start it carries is the provisioner's clock.
     */
    public static String generate(Clock clock) {
        return TimeOrderedEpochFactory.builder()
                .withClock(clock)
                .withIncrementPlus1()
                .build()
                .create()
                .toString();
    }

    /**
     * When an id {@link #generate()} produced was generated, to the millisecond — what a session
     * directory's name says about when the provisioner started it. Ids of one process order as
     * their strings do, and within a millisecond the counter keeps that order.
     *
     * @return the id's time, or empty for anything that is not a canonical time-ordered UUID
     */
    public static Optional<Instant> timestampOf(String id) {
        if (id == null) {
            return Optional.empty();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // fromString is lenient about field widths and case; only the canonical form is an id
        boolean canonical = uuid.toString().equals(id);
        if (!canonical || uuid.version() != TIME_ORDERED_VERSION || uuid.variant() != STANDARD_VARIANT) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> TIMESTAMP_SHIFT));
    }
}
