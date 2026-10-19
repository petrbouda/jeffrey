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
 * How many throwables of one class were constructed from one stack on one thread — the finest grain
 * the Exceptions page and MCP aggregate from, so per-class and per-site distinct thread counts can be
 * computed exactly rather than summed.
 *
 * @param thrownClass    the class that was thrown
 * @param error          whether the throws were recorded as {@code jdk.JavaErrorThrow}
 * @param stacktraceHash reference into the stacktraces table; null when no stack was captured
 * @param threadHash     identity hash of the throwing thread; null when the event carried none
 * @param count          throwables, one per object constructed — an Error's twin events collapsed
 */
public record ThrowCountRecord(
        String thrownClass,
        boolean error,
        Long stacktraceHash,
        Long threadHash,
        long count) {
}
