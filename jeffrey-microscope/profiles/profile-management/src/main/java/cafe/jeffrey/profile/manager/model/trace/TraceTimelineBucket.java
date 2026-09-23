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
 * One slice of the recording, with what the traces starting inside it did — what the trace list
 * plots to show when the profile was busy and when it was slow.
 *
 * @param fromMillisFromBeginning where the slice starts, relative to the recording's start, so it
 *                                lines up with every other timeline in the profile without
 *                                converting
 * @param count                   how many traces started inside it
 * @param errorCount              how many of those failed
 * @param maxDurationNanos        the slowest of them
 */
public record TraceTimelineBucket(
        long fromMillisFromBeginning,
        long count,
        long errorCount,
        long maxDurationNanos) {
}
