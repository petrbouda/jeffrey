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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

import java.time.Duration;
import java.util.Optional;

/**
 * One recording's span on the UTC epoch-millisecond base, and the one place a window given on that
 * base is checked against the recording and turned into the offsets from its start that the
 * flamegraph, timeseries and sub-second managers read.
 * <p>
 * The window inputs are instants because every time an answer hands out is one: a caller copies
 * {@code startEpochMs} from {@code timeline_hotWindows} or {@code profiles_get} into the next call
 * without arithmetic. The conversion to an offset happens here, at the tool boundary, and a bound
 * outside the recording is refused with the recording's own span, so the refusal is also the
 * correction. The reverse, an offset a manager reports placed on the epoch base, is
 * {@link #epochAt}.
 *
 * @param profileId    the profile the recording belongs to, named in every refusal
 * @param startEpochMs when the recording started
 * @param endEpochMs   when it finished
 */
public record RecordingSpan(String profileId, long startEpochMs, long endEpochMs) {

    private static final String START_INPUT = "startEpochMs";
    private static final String END_INPUT = "endEpochMs";

    private static final String NO_SPAN =
            "Profile %s carries no recording span (a start before an end), so a window cannot be placed on it; "
                    + "omit startEpochMs and endEpochMs to use the whole recording.";
    private static final String OUTSIDE =
            "%s=%d lies outside profile %s's recording, which spans %d..%d (UTC epoch ms). "
                    + "Pass a window inside that span, or omit the bound to use the recording's own.";
    private static final String PAST_OTHER_END =
            "The window reaches %d ms past the start of the primary's recording, beyond the end of "
                    + "profile %s's recording, which spans %d..%d (UTC epoch ms) and is %d ms long. A "
                    + "comparison window is applied at the same offset into both recordings, so it "
                    + "must lie within the first %d ms of the primary's.";

    public RecordingSpan {
        if (endEpochMs <= startEpochMs) {
            throw new IllegalArgumentException("A recording must end after it starts: profileId="
                    + profileId + " startEpochMs=" + startEpochMs + " endEpochMs=" + endEpochMs);
        }
    }

    /**
     * The recording's span, or nothing when it never recorded when it ran — an import without
     * timestamps, or a parse that never reached the end — or ran for less than a millisecond.
     */
    public static Optional<RecordingSpan> of(ProfileInfo info) {
        if (info.profilingStartedAt() == null || info.profilingFinishedAt() == null) {
            return Optional.empty();
        }
        long start = info.profilingStartedAt().toEpochMilli();
        long end = info.profilingFinishedAt().toEpochMilli();
        // Compared on the millisecond base the span lives on: a recording shorter than a millisecond
        // is no span there, reported as none rather than refused by the invariant below.
        if (end <= start) {
            return Optional.empty();
        }
        return Optional.of(new RecordingSpan(info.id(), start, end));
    }

    /**
     * The recording's span, for a caller that is about to place a window on it.
     *
     * @throws IllegalArgumentException when the recording carries no span, saying to omit the window
     */
    static RecordingSpan require(ProfileInfo info) {
        return of(info).orElseThrow(() -> new IllegalArgumentException(NO_SPAN.formatted(info.id())));
    }

    long durationMs() {
        return endEpochMs - startEpochMs;
    }

    /** The whole recording as a window. */
    EpochWindow whole() {
        return new EpochWindow(startEpochMs, endEpochMs);
    }

    /**
     * A window given on the epoch base, checked against this recording. An omitted bound is the
     * recording's own.
     *
     * @throws IllegalArgumentException when a bound lies outside the recording, naming its span, or the
     *                                  window ends where it starts
     */
    EpochWindow window(Long start, Long end) {
        long from = start == null ? startEpochMs : inside(START_INPUT, start);
        long to = end == null ? endEpochMs : inside(END_INPUT, end);
        return new EpochWindow(from, to);
    }

    /**
     * The window at these offsets from this recording's start, for a comparison that applies one
     * window at the same offset into both recordings. An open end is this recording's own.
     *
     * @throws IllegalArgumentException when an offset reaches past this recording's end
     */
    EpochWindow windowAt(long fromOffsetMs, Long toOffsetMs) {
        long reach = toOffsetMs == null ? fromOffsetMs : toOffsetMs;
        if (reach > durationMs() || (toOffsetMs == null && fromOffsetMs >= durationMs())) {
            throw new IllegalArgumentException(PAST_OTHER_END.formatted(
                    reach, profileId, startEpochMs, endEpochMs, durationMs(), durationMs()));
        }
        return new EpochWindow(startEpochMs + fromOffsetMs,
                toOffsetMs == null ? endEpochMs : startEpochMs + toOffsetMs);
    }

    /** How far into the recording an instant on it lies. */
    long offsetOf(long epochMs) {
        return epochMs - startEpochMs;
    }

    /** The instant this far into the recording. */
    /**
     * The instant an offset from the recording's start falls on: how an offset a manager reports, such
     * as a thread dump's or a launched process's, reaches an answer as UTC epoch milliseconds.
     */
    public long epochAt(long offsetMs) {
        return startEpochMs + offsetMs;
    }

    /** A window on this recording as the offsets from its start that the managers read. */
    RelativeTimeRange offsets(EpochWindow window) {
        return new RelativeTimeRange(
                Duration.ofMillis(offsetOf(window.startEpochMs())),
                Duration.ofMillis(offsetOf(window.endEpochMs())));
    }

    private long inside(String input, long epochMs) {
        if (epochMs < startEpochMs || epochMs > endEpochMs) {
            throw new IllegalArgumentException(
                    OUTSIDE.formatted(input, epochMs, profileId, startEpochMs, endEpochMs));
        }
        return epochMs;
    }
}
