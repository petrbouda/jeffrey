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

import java.util.List;

/**
 * Every notification of one kind across many traces, as a reader sees it: what the application kept
 * saying, how often, in how many requests, and a few requests to open to see it in context.
 * <p>
 * Trace ids are hex strings for the same reason a span's are: they are 64-bit values that exceed
 * JavaScript's safe-integer range, and the same hex is what {@code traces_traceExport} takes.
 *
 * @param type                     what kind of thing happened, as the emitter named it
 * @param severity                 {@code CRITICAL}, {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param category                 what area it concerns; may be null
 * @param source                   the component that raised it; may be null
 * @param message                  the sentence every occurrence of the kind carries; may be null
 * @param count                    how many times it was raised
 * @param traceCount               how many distinct traces raised it at least once
 * @param firstMillisFromBeginning when the earliest fired, relative to the recording's start
 * @param lastMillisFromBeginning  when the latest fired, relative to the recording's start
 * @param exemplarTraceIds         a few traces that raised it, the slowest first
 */
public record TraceNotificationGroupRow(
        String type,
        String severity,
        String category,
        String source,
        String message,
        long count,
        long traceCount,
        long firstMillisFromBeginning,
        long lastMillisFromBeginning,
        List<String> exemplarTraceIds) {
}
