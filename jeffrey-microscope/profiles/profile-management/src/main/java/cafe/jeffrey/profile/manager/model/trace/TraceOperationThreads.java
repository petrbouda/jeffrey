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
 * How an operation's spans divide across threads. Only the platform-thread spans can carry a
 * flamegraph: the profiler attributes every sample to the carrier, never to the virtual thread
 * mounted on it.
 *
 * A span whose thread could not be resolved is counted separately rather than as a platform span:
 * not knowing where it ran is not the same as knowing samples are available for it.
 *
 * @param distinctThreads how many threads the operation's spans ran on
 * @param platformSpans   spans on a platform thread
 * @param virtualSpans    spans on a virtual thread
 * @param unknownSpans    spans whose thread could not be resolved
 */
public record TraceOperationThreads(
        long distinctThreads, long platformSpans, long virtualSpans, long unknownSpans) {
}
