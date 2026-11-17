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
