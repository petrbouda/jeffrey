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

import java.util.List;

/**
 * Aggregated throw statistics for one exception class, derived from {@code jdk.JavaExceptionThrow}
 * and {@code jdk.JavaErrorThrow} events — one per throwable constructed, an Error's twin events
 * collapsed.
 *
 * @param thrownClass      binary name of the thrown class
 * @param count            number of sampled throws of this class
 * @param error            whether the class was recorded as an {@code Error} throw
 * @param messages         the commonest distinct messages with their occurrence counts (may be empty)
 * @param omittedMessages  distinct messages left out of {@code messages} by its cap
 * @param threadCount      number of distinct threads that threw this class
 * @param applicationCount throws of this class the application (or a library it uses) raised
 * @param jvmInternalCount throws of this class the JVM's loading and linking machinery raised
 */
public record ExceptionTypeStat(
        String thrownClass,
        long count,
        boolean error,
        List<ExceptionMessageCount> messages,
        int omittedMessages,
        int threadCount,
        long applicationCount,
        long jvmInternalCount) {
}
