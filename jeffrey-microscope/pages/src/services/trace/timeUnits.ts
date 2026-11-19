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
