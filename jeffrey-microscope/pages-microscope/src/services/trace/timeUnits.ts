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

/**
 * The conversions the trace views need, in one place.
 *
 * Traces cross three units and the boundaries matter: durations arrive in nanoseconds, spans are
 * placed in microseconds because a span is routinely shorter than a millisecond, and the events
 * table is keyed in milliseconds. Each factor had been redeclared in every file that needed it,
 * which is how a start came to be floored in one place and rounded in another for the same window.
 */

export const NANOS_PER_MICRO = 1_000;

export const NANOS_PER_MILLI = 1_000_000;

export const MICROS_PER_MILLI = 1_000;

/**
 * Floors microseconds to the millisecond a sample taken at that instant was filed under — the right
 * rounding for the *start* of a window, which must not exclude what happened at its beginning.
 */
export function floorToMillis(micros: number): number {
  return Math.floor(micros / MICROS_PER_MILLI);
}

/**
 * Ceils nanoseconds to a whole millisecond — the right rounding for the *end* of a window. Rounding
 * it to nearest could land before the work actually finished and drop the last events of a span.
 */
export function ceilNanosToMillis(nanos: number): number {
  return Math.ceil(nanos / NANOS_PER_MILLI);
}
