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
