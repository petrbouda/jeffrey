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
