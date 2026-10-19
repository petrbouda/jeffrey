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

/**
 * Headline exception metrics for a profile.
 *
 * @param totalThrowables        cumulative throwables created since JVM start (from the periodic
 *                               {@code jdk.ExceptionStatistics} gauge; counts every constructed
 *                               throwable, far more than the sampled throw events)
 * @param sampledThrowCount      sampled throws of a class that is not an {@code Error}
 * @param errorCount             sampled throws of an {@code Error}, each counted once
 * @param distinctTypes          distinct thrown classes across the sampled throw events
 * @param hasExceptionThrowEvents whether per-throw {@code jdk.JavaExceptionThrow} events are present
 * @param hasErrorThrowEvents    whether {@code jdk.JavaErrorThrow} events are present
 * @param applicationThrowCount  sampled throws the application (or a library it uses) raised
 * @param jvmInternalThrowCount  sampled throws the JVM's loading and linking machinery raised
 */
public record ExceptionsOverview(
        long totalThrowables,
        long sampledThrowCount,
        long errorCount,
        int distinctTypes,
        boolean hasExceptionThrowEvents,
        boolean hasErrorThrowEvents,
        long applicationThrowCount,
        long jvmInternalThrowCount) {
}
