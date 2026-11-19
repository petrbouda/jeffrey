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
 * Geometry for a percentile spread drawn on a shared rail: where each percentile sits, and how wide
 * the band between two of them is.
 *
 * Percentages of a scale the caller owns, rather than of each row's own maximum. That is the whole
 * point of the visual — every row measured against the same rail, so a slow operation looks slow
 * next to a fast one instead of every row filling its own width.
 */

/** A band this narrow would round away to nothing, so it is floored to stay visible. */
export const MIN_BAND_PERCENT = 0.5;

/** Where a value sits on the rail, clamped so an outlier cannot overhang the right edge. */
export function positionPercent(value: number, scale: number): number {
  if (scale <= 0) {
    return 0;
  }
  return Math.min((value / scale) * 100, 100);
}

/**
 * The width of the band between two percentiles. An operation with no spread — every call the same
 * duration — still gets {@link MIN_BAND_PERCENT}, so the band reads as "tight", not as "missing".
 */
export function bandWidthPercent(from: number, to: number, scale: number): number {
  if (scale <= 0) {
    return 0;
  }
  return Math.max(((to - from) / scale) * 100, MIN_BAND_PERCENT);
}
