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
package cafe.jeffrey.profile.manager.model.trace;

/**
 * The stretch of time a trace occupied — first span start to last span end, absolute.
 * <p>
 * Sent rather than worked out by the reader from the spans it holds: a trace whose big runs arrive
 * folded hands the reader only some of its spans, and an axis derived from those would shift every
 * time a run's members were loaded into it.
 *
 * @param startEpochMicros where the trace began, as UTC epoch micros
 * @param endEpochMicros   where its last span ended, as UTC epoch micros
 */
public record TraceWindow(long startEpochMicros, long endEpochMicros) {

    public TraceWindow {
        if (endEpochMicros < startEpochMicros) {
            throw new IllegalArgumentException(
                    "Window ends before it starts: start=" + startEpochMicros + " end=" + endEpochMicros);
        }
    }

    /**
     * @return the window's length, never less than one microsecond so it can always divide
     */
    public long lengthMicros() {
        return Math.max(1, endEpochMicros - startEpochMicros);
    }
}
