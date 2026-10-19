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

package cafe.jeffrey.profile.manager.model.exceptions;

import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;

import java.util.List;

/**
 * One throw site: an exception class together with the frame that threw it. Different stacks that
 * reach the same {@code throw} statement collapse into one site, because that line is what a reader
 * changes.
 *
 * @param thrownClass     binary name of the thrown class
 * @param error           whether the class was recorded as an {@code Error} throw
 * @param origin          whose throw it was, see {@code JvmInternalThrows}
 * @param throwingFrame   the frame that contained the {@code throw}; null when no stack was captured
 * @param stacktraceId    the site's commonest stack, as the hex id the stack endpoint reads; null when
 *                        no stack was captured
 * @param count           throws from this site
 * @param threadCount     distinct threads that threw from this site
 * @param messages        the commonest distinct messages with their occurrence counts (may be empty)
 * @param omittedMessages distinct messages left out of {@code messages} by its cap
 */
public record ExceptionSiteStat(
        String thrownClass,
        boolean error,
        ThrowOrigin origin,
        ThrowFrame throwingFrame,
        String stacktraceId,
        long count,
        int threadCount,
        List<ExceptionMessageCount> messages,
        int omittedMessages) {
}
