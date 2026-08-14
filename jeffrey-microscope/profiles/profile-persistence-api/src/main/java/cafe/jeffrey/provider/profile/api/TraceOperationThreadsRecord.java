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

package cafe.jeffrey.provider.profile.api;

/**
 * How an operation's spans are spread across threads.
 * <p>
 * The platform/virtual split is not trivia: a sample is matched to a span by thread, and the
 * profiler attributes samples to the carrier thread, so only the platform-thread spans can ever
 * carry a flamegraph.
 *
 * Unknown is its own bucket rather than being folded into the platform count. A span whose thread
 * did not resolve is not evidence that samples are available; counting it as one promised a
 * flamegraph that comes back empty.
 *
 * @param distinctThreads how many threads the operation's spans ran on
 * @param platformSpans   spans that ran on a platform thread
 * @param virtualSpans    spans that ran on a virtual thread
 * @param unknownSpans    spans whose thread could not be resolved
 */
public record TraceOperationThreadsRecord(
        long distinctThreads, long platformSpans, long virtualSpans, long unknownSpans) {

    public static final TraceOperationThreadsRecord EMPTY = new TraceOperationThreadsRecord(0, 0, 0, 0);
}
