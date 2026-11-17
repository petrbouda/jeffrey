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

package cafe.jeffrey.provider.profile.api;

import java.util.List;

/**
 * Every notification of one kind across the traces a query selected, collapsed into one row.
 * <p>
 * A kind is the tuple {@code (type, severity, category, source, message)}: Jeffrey's own emitter keeps
 * the message constant per type, but a third-party one may not, and a type that carried two sentences
 * is two findings rather than one with a sentence picked at random.
 *
 * @param count                    how many notifications of this kind there were
 * @param traceCount               how many distinct traces raised at least one
 * @param firstMillisFromBeginning when the earliest fired, relative to the recording's start
 * @param lastMillisFromBeginning  when the latest fired, relative to the recording's start
 * @param exemplarTraceIds         a few traces that raised it, the slowest first, as candidates for a
 *                                 trace export
 */
public record TraceNotificationGroupRecord(
        String type,
        String severity,
        String category,
        String source,
        String message,
        long count,
        long traceCount,
        long firstMillisFromBeginning,
        long lastMillisFromBeginning,
        List<Long> exemplarTraceIds) {

    public TraceNotificationGroupRecord {
        exemplarTraceIds = List.copyOf(exemplarTraceIds);
    }
}
