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

package cafe.jeffrey.shared.common.model.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Narrows a recording-session listing. Every field is optional: a {@code null} bound, a
 * {@code null} status or a zero limit does not restrict anything, so {@link #ALL} returns the
 * listing unchanged.
 *
 * <p>The time window uses <em>overlap</em> semantics rather than "created inside the window": a
 * session matches when it was recording at any point between {@code activeFrom} and
 * {@code activeTo}. A JVM that started three hours ago and is still running therefore matches
 * "the last hour", which is what a reader asking for recent recordings expects; a session that
 * finished before the window opened does not.
 *
 * <p>The filter is applied on the hub, so the listing that travels over gRPC is already the one
 * the caller asked for. {@link #apply(List)} expects the sessions newest first, which is how the
 * hub storage sorts them; the limit keeps the newest ones.
 *
 * @param activeFrom the session must not have finished before this instant, or {@code null}
 * @param activeTo   the session must have started before this instant, or {@code null}
 * @param status     the session must be in this status, or {@code null} for any status
 * @param limit      keep at most this many sessions, or {@link #NO_LIMIT}
 */
public record RecordingSessionFilter(
        Instant activeFrom,
        Instant activeTo,
        RecordingStatus status,
        int limit) {

    public static final int NO_LIMIT = 0;

    public static final RecordingSessionFilter ALL = new RecordingSessionFilter(null, null, null, NO_LIMIT);

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
     * Sessions that were recording at any point during the last {@code lookBack}, ending at
     * {@code now}. The window is left open at the end so a session that starts between the
     * hub's and the caller's clock readings is not lost.
     */
    public static RecordingSessionFilter activeWithinLast(Duration lookBack, Instant now) {
        if (lookBack.isNegative()) {
            throw new IllegalArgumentException("Look-back must not be negative: look_back=" + lookBack);
        }
        return new RecordingSessionFilter(now.minus(lookBack), null, null, NO_LIMIT);
    }

    public RecordingSessionFilter withStatus(RecordingStatus newStatus) {
        return new RecordingSessionFilter(activeFrom, activeTo, newStatus, limit);
    }

    public RecordingSessionFilter withLimit(int newLimit) {
        return new RecordingSessionFilter(activeFrom, activeTo, status, newLimit);
    }

    public boolean isUnrestricted() {
        return activeFrom == null && activeTo == null && status == null && limit == NO_LIMIT;
    }

    /**
     * Whether a single session satisfies the window and status constraints. The limit is a
     * property of the whole listing and is not consulted here.
     */
    public boolean matches(RecordingSession session) {
        if (status != null && session.status() != status) {
            return false;
        }
        if (activeTo != null && session.createdAt().isAfter(activeTo)) {
            return false;
        }
        if (activeFrom != null && session.finishedAt() != null && session.finishedAt().isBefore(activeFrom)) {
            return false;
        }
        return true;
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
