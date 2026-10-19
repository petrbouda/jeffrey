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

import cafe.jeffrey.profile.common.exceptions.ThrowFrame;

import java.util.List;
import java.util.Map;

/**
 * The profile's sampled throws ({@code jdk.JavaExceptionThrow} and {@code jdk.JavaErrorThrow}),
 * aggregated and with their stacks, for the Exceptions page and MCP.
 * <p>
 * Every count here is one per throwable constructed: an Error emits two {@code jdk.JavaExceptionThrow}
 * and one {@code jdk.JavaErrorThrow}, and the reads collapse those back to one — the same
 * de-duplication the trace derivation applies.
 */
public interface ProfileExceptionRepository {

    /**
     * Throw counts by class, stack and thread; empty when neither throw event was recorded.
     */
    List<ThrowCountRecord> throwCounts();

    /**
     * Throw counts by class, stack and non-blank message.
     */
    List<ThrowMessageRecord> throwMessages();

    /**
     * Every stack a throw was recorded with, keyed by its hash, the frame on top of the stack first.
     */
    Map<Long, List<ThrowFrame>> throwStacktraces();
}
