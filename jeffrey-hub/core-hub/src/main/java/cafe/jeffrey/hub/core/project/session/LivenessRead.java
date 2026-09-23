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

package cafe.jeffrey.hub.core.project.session;

import java.time.Instant;
import java.util.Optional;

/**
 * The outcome of reading one liveness file.
 *
 * <p>Three states rather than an {@code Optional}, because the two ways of having no timestamp
 * mean opposite things to {@link SessionFinisher}. <b>Absent</b> is evidence: nothing has ever
 * written here, and past the startup deadline that says the producer never got far enough to
 * report. <b>Unreadable</b> is the absence of evidence: a file is there, or may be, and this hub
 * could not make sense of it — a permission error, a stale handle on a network mount, a truncated
 * read, a timestamp that is not a number.</p>
 *
 * <p>Collapsing the two is how a mount blip turns a four-hour session into a zero-duration one:
 * the finisher stamps {@code originCreatedAt}, and since only unfinished sessions are ever
 * revisited, that wrong instant is permanent.</p>
 */
public sealed interface LivenessRead {

    /** The file was there and carried a timestamp. */
    record Reported(Instant at) implements LivenessRead {
    }

    /** Nothing has been written to this path. */
    record Absent() implements LivenessRead {

        private static final Absent INSTANCE = new Absent();
    }

    /**
     * Something is there, or may be, and this hub could not read it.
     *
     * @param reason what went wrong, for the log line that says why a session was left alone
     */
    record Unreadable(String reason) implements LivenessRead {
    }

    static LivenessRead absent() {
        return Absent.INSTANCE;
    }

    static LivenessRead reported(Instant at) {
        return new Reported(at);
    }

    static LivenessRead unreadable(String reason) {
        return new Unreadable(reason);
    }

    /** The timestamp when there is one, for a caller that treats both failures the same way. */
    default Optional<Instant> timestamp() {
        return this instanceof Reported reported ? Optional.of(reported.at()) : Optional.empty();
    }

    /** Whether this read is evidence that nothing was written, rather than a failure to look. */
    default boolean isAbsent() {
        return this instanceof Absent;
    }
}
