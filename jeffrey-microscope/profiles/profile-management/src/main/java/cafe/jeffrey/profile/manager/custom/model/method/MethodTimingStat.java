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

package cafe.jeffrey.profile.manager.custom.model.method;

/**
 * One method's timing tally, as {@code jdk.MethodTiming} counted it.
 * <p>
 * Every figure here is <b>exact and complete</b>, which is what makes this worth showing beside the
 * sampled surfaces on the same dashboard. {@code jdk.MethodTrace} writes an event per invocation, so
 * it costs in proportion to how often the method is called and is usually pointed at a handful of
 * methods; {@code jdk.MethodTiming} instruments the method to keep running counters and reports them
 * periodically, so it can watch a method called a million times for a fixed price. The trade is that
 * it keeps no stack, no thread and no individual invocation — it can say a method was called 4.2
 * million times averaging 3&nbsp;µs, and can never say who called it or when the slow one happened.
 *
 * @param className    the declaring class
 * @param methodName   the method
 * @param invocations  how many times it was called over the whole recording
 * @param minNanos     the fastest call
 * @param avgNanos     the mean across every call, as the JVM computed it. Not derivable from
 *                     anything else here, and not combinable across recordings
 * @param maxNanos     the slowest call
 */
public record MethodTimingStat(
        String className,
        String methodName,
        long invocations,
        long minNanos,
        long avgNanos,
        long maxNanos) {
}
