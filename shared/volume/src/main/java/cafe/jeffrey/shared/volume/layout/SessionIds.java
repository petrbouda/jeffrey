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

package cafe.jeffrey.shared.volume.layout;

import com.github.f4b6a3.uuid.factory.standard.TimeOrderedEpochFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A session directory's name: a time-ordered UUID (version 7) the provisioner generates when the
 * session starts, so the start is in the name and nothing on the volume has to be read to know it.
 * The provisioner writes it ({@link #generate}), every reader reads the start back
 * ({@link #timestampOf}) — {@link ProjectTree} takes a directory for a session exactly when its
 * name carries one.
 */
public abstract class SessionIds {

    /** The UUID version a session id is: time-ordered, epoch millis in the top 48 bits. */
    private static final int TIME_ORDERED_VERSION = 7;

    /** The RFC 9562 variant every session id carries. */
    private static final int STANDARD_VARIANT = 2;

    /** How far the epoch millis lie above the version and counter bits of the most significant half. */
    private static final int TIMESTAMP_SHIFT = 16;

    /**
     * A new session id carrying the time {@code clock} reads, so the start a session's name says is
     * the provisioner's clock. See
     * <a href="https://github.com/f4b6a3/uuid-creator/wiki/1.7.-UUIDv7#type-2-plus-1">UUIDv7, type 2 plus 1</a>.
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
     * When a session id was generated, to the millisecond — what a session directory's name says
     * about when the provisioner started it.
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
