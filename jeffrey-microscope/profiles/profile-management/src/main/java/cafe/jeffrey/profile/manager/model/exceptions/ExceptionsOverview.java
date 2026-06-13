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

package cafe.jeffrey.profile.manager.model.exceptions;

/**
 * Headline exception metrics for a profile.
 *
 * @param totalThrowables        cumulative throwables created since JVM start (from the periodic
 *                               {@code jdk.ExceptionStatistics} gauge; counts every constructed
 *                               throwable, far more than the sampled throw events)
 * @param sampledThrowCount      number of {@code jdk.JavaExceptionThrow} events in the recording
 * @param errorCount             number of {@code jdk.JavaErrorThrow} events in the recording
 * @param distinctTypes          distinct thrown classes across the sampled throw events
 * @param hasExceptionThrowEvents whether per-throw {@code jdk.JavaExceptionThrow} events are present
 * @param hasErrorThrowEvents    whether {@code jdk.JavaErrorThrow} events are present
 */
public record ExceptionsOverview(
        long totalThrowables,
        long sampledThrowCount,
        long errorCount,
        int distinctTypes,
        boolean hasExceptionThrowEvents,
        boolean hasErrorThrowEvents) {
}
