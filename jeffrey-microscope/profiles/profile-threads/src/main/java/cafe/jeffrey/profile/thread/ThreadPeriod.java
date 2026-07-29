/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.thread;

import java.time.Duration;

/**
 * One drawable band on a thread's timeline, in nanoseconds from the start of the recording.
 *
 * <p>A band covers one or more source events of a single category: events too close together to be
 * told apart at the timeline's resolution are merged by {@link ThreadBands}, and {@code eventCount}
 * records how many went in. The fields of those events are deliberately absent — they are fetched
 * for the hovered window when a tooltip actually needs them, because carrying them here meant
 * repeating every file path and socket host once per event across the whole response.
 *
 * @param startOffset offset of the band's start from the beginning of the recording, in nanoseconds
 * @param width       length of the band in nanoseconds, always at least 1
 * @param eventCount  how many source events the band covers. Merging chains, so on a busy lane this
 *                    is the total for one long run of activity — it is not a count for any position
 *                    inside the band, and a tooltip must not read it as one
 */
public record ThreadPeriod(long startOffset, long width, int eventCount) {

    public ThreadPeriod {
        if (eventCount < 1) {
            throw new IllegalArgumentException("A band must cover at least one event: " + eventCount);
        }
    }

    public ThreadPeriod(Duration startOffset, Duration endOffset) {
        this(startOffset.toNanos(), Math.max(endOffset.minus(startOffset).toNanos(), 1), 1);
    }

    public long endOffset() {
        return startOffset + width;
    }
}
