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

package cafe.jeffrey.profile.thread;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges a thread's events into the bands the timeline can actually draw.
 *
 * <p>The timeline maps the whole recording onto a canvas a few thousand pixels wide, so two events
 * closer together than one pixel are the same rectangle on screen — but shipping them separately
 * costs a full JSON object each, and a busy thread can produce thousands per category. Merging them
 * server-side bounds the response by the timeline's resolution instead of by the recording's event
 * count, and the merged band keeps the event count so a tooltip can still say how many events it
 * covers.
 *
 * <p>The resolution is deliberately finer than any realistic canvas: at {@link #RESOLUTION} buckets
 * a bucket is under a pixel wide on a canvas that wide, so nothing that would be visible as a gap
 * gets merged away.
 *
 * <p>The merge <em>chains</em>, though: a band keeps absorbing while the next event starts within a
 * bucket of the band's current end, so a thread that is continuously busy collapses into a single
 * band spanning most of the recording. That is the right picture to draw — the gaps really are
 * invisible — but it means a band's {@code eventCount} is the total for a whole run of activity and
 * says nothing about any position inside it. Anything that has to answer "what is happening at this
 * point" must ask by time window instead; see {@link ThreadWindowEvents}.
 */
public final class ThreadBands {

    /**
     * How many buckets the recording is divided into. Events landing in the same bucket, or in
     * adjacent ones, cannot be told apart on a canvas of this width and are merged into one band.
     */
    private static final int RESOLUTION = 4000;

    private static final long MIN_BUCKET_WIDTH_NANOS = 1;

    private final long bucketWidthNanos;

    private ThreadBands(long bucketWidthNanos) {
        this.bucketWidthNanos = bucketWidthNanos;
    }

    /**
     * Bands sized for a recording of the given length.
     */
    public static ThreadBands forRecording(Duration recordingDuration) {
        long durationNanos = recordingDuration == null ? 0 : recordingDuration.toNanos();
        return new ThreadBands(Math.max(durationNanos / RESOLUTION, MIN_BUCKET_WIDTH_NANOS));
    }

    /**
     * Merges periods that are indistinguishable at the timeline's resolution.
     *
     * @param periods single-event periods of one category, ordered by start offset
     * @return the drawable bands, ordered by start offset
     */
    public List<ThreadPeriod> merge(List<ThreadPeriod> periods) {
        if (periods.size() < 2) {
            return periods;
        }

        List<ThreadPeriod> bands = new ArrayList<>();
        OpenBand open = new OpenBand(periods.getFirst());
        for (ThreadPeriod period : periods.subList(1, periods.size())) {
            if (open.touches(period, bucketWidthNanos)) {
                open.absorb(period);
            } else {
                bands.add(open.close());
                open = new OpenBand(period);
            }
        }
        bands.add(open.close());
        return bands;
    }

    /**
     * The band currently being extended. Kept mutable and private so the public model stays an
     * immutable record.
     */
    private static final class OpenBand {

        private final long startOffset;
        private long endOffset;
        private int eventCount;

        private OpenBand(ThreadPeriod first) {
            this.startOffset = first.startOffset();
            this.endOffset = first.endOffset();
            this.eventCount = first.eventCount();
        }

        /**
         * Whether the next period would render against this band with no gap between them — either
         * overlapping it or starting within one bucket of its end.
         */
        private boolean touches(ThreadPeriod period, long bucketWidthNanos) {
            return period.startOffset() - endOffset <= bucketWidthNanos;
        }

        private void absorb(ThreadPeriod period) {
            this.endOffset = Math.max(this.endOffset, period.endOffset());
            this.eventCount += period.eventCount();
        }

        private ThreadPeriod close() {
            return new ThreadPeriod(startOffset, Math.max(endOffset - startOffset, 1), eventCount);
        }
    }
}
