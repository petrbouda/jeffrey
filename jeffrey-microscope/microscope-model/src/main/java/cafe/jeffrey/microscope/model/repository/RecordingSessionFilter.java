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

package cafe.jeffrey.microscope.model.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Narrows a recording-session listing. Every field is optional: a {@code null} bound or a zero
 * limit does not restrict anything, so {@link #ALL} returns the listing unchanged.
 *
 * <p>The time window uses <em>overlap</em> semantics rather than "created inside the window": a
 * session matches when its recorded span — from its start to the end of its data — touches the
 * window between {@code activeFrom} and {@code activeTo}. A JVM that started three hours ago and
 * recorded its last chunk ten minutes ago therefore matches "the last hour", which is what a
 * reader asking for recent recordings expects; a session whose data ended before the window
 * opened does not. A session with no data yet reaches only as far as its start.
 *
 * <p>The filter is applied where the sessions are read, so the listing handed back is already the
 * one the caller asked for. {@link #apply(List)} expects the sessions newest first, which is how
 * the volume's layout sorts them; the limit keeps the newest ones.
 *
 * @param activeFrom the session's data must not end before this instant, or {@code null}
 * @param activeTo   the session must have started before this instant, or {@code null}
 * @param limit      keep at most this many sessions, or {@link #NO_LIMIT}
 */
public record RecordingSessionFilter(Instant activeFrom, Instant activeTo, int limit) {

    public static final int NO_LIMIT = 0;

    public static final RecordingSessionFilter ALL = new RecordingSessionFilter(null, null, NO_LIMIT);

    public RecordingSessionFilter {
        if (limit < NO_LIMIT) {
            throw new IllegalArgumentException("Session limit must not be negative: limit=" + limit);
        }
        if (activeFrom != null && activeTo != null && activeFrom.isAfter(activeTo)) {
            throw new IllegalArgumentException(
                    "Session window is empty, activeFrom is after activeTo: active_from="
                            + activeFrom + " active_to=" + activeTo);
        }
    }

    /**
     * Sessions whose data reaches into the last {@code lookBack}, ending at {@code now}. The window
     * is left open at the end so a session that starts between the volume's and the caller's
     * clock readings is not lost.
     */
    public static RecordingSessionFilter activeWithinLast(Duration lookBack, Instant now) {
        if (lookBack.isNegative()) {
            throw new IllegalArgumentException("Look-back must not be negative: look_back=" + lookBack);
        }
        return new RecordingSessionFilter(now.minus(lookBack), null, NO_LIMIT);
    }

    public RecordingSessionFilter withLimit(int newLimit) {
        return new RecordingSessionFilter(activeFrom, activeTo, newLimit);
    }

    public boolean isUnrestricted() {
        return activeFrom == null && activeTo == null && limit == NO_LIMIT;
    }

    /**
     * Whether a single session satisfies the window. The limit is a property of the whole listing
     * and is not consulted here.
     */
    public boolean matches(RecordingSession session) {
        if (activeTo != null && session.createdAt().isAfter(activeTo)) {
            return false;
        }
        Instant reaches = session.lastDataAt() != null ? session.lastDataAt() : session.createdAt();
        return activeFrom == null || !reaches.isBefore(activeFrom);
    }

    /**
     * Keeps the sessions that {@link #matches(RecordingSession) match}, preserving their order,
     * and cuts the result to {@link #limit()} when one is set.
     *
     * @param sessionsNewestFirst the full listing, newest session first
     */
    public List<RecordingSession> apply(List<RecordingSession> sessionsNewestFirst) {
        if (isUnrestricted()) {
            return sessionsNewestFirst;
        }
        var matching = sessionsNewestFirst.stream().filter(this::matches);
        if (limit != NO_LIMIT) {
            matching = matching.limit(limit);
        }
        return matching.toList();
    }
}
