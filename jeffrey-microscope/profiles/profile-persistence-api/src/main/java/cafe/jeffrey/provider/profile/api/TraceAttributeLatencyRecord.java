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

package cafe.jeffrey.provider.profile.api;

/**
 * How many traces of one value landed in one duration bucket — a single cell of the latency
 * heatmap.
 * <p>
 * Buckets are half-decades of nanoseconds: {@code bucket = floor(2 * log10(nanos))}, clamped at both
 * ends so the grid stays a fixed width whatever the recording holds. Half-decades rather than
 * decades because a decade is too coarse to separate a 12 ms population from a 90 ms one, and those
 * are exactly the two a reader is trying to tell apart.
 *
 * @param value      the value the traces carried
 * @param bucket     the half-decade index; the caller renders its bounds
 * @param traceCount how many traces of that value fell in it
 */
public record TraceAttributeLatencyRecord(String value, int bucket, long traceCount) {
}
