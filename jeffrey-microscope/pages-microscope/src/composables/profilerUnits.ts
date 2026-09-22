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
 * The units the Profiler Builder offers, in one table. The unit selects are rendered from it, the
 * badges take their labels from it, and the command takes its suffixes and scales from it, so a
 * select, a badge and the emitted option cannot disagree about a unit.
 */
export interface OptionUnit {
  /** The value a unit select stores. */
  readonly unit: string;
  /** How the builder shows the unit, in selects and badges alike. */
  readonly label: string;
  /** The suffix written into the command; async-profiler reads its first character. */
  readonly suffix: string;
  /** How many base units (nanoseconds or bytes) one of these holds. */
  readonly scale: number;
}

/** async-profiler's NANOS table, largest first. The last row is the base unit and always fits. */
export const DURATION_UNITS: readonly OptionUnit[] = [
  { unit: 's', label: 's', suffix: 's', scale: 1_000_000_000 },
  { unit: 'ms', label: 'ms', suffix: 'ms', scale: 1_000_000 },
  { unit: 'us', label: 'µs', suffix: 'us', scale: 1_000 },
  { unit: 'ns', label: 'ns', suffix: 'ns', scale: 1 }
];

/**
 * async-profiler's BYTES table, largest first. Its k and m are binary multiples, hence KiB and
 * MiB. A bare number means bytes.
 */
export const BYTE_UNITS: readonly OptionUnit[] = [
  { unit: 'mb', label: 'MiB', suffix: 'm', scale: 1024 * 1024 },
  { unit: 'kb', label: 'KiB', suffix: 'k', scale: 1024 },
  { unit: 'b', label: 'B', suffix: '', scale: 1 }
];

function offered(table: readonly OptionUnit[], units: readonly string[]): readonly OptionUnit[] {
  return units.map(unit => {
    const found = table.find(candidate => candidate.unit === unit);
    if (found === undefined) {
      throw new Error(`Unknown profiler unit: unit=${unit}`);
    }
    return found;
  });
}

/** The CPU sampling interval. */
export const SAMPLING_INTERVAL_UNITS = offered(DURATION_UNITS, ['us', 'ms']);

/** Wall sampling interval, lock wait threshold and trace latency threshold. */
export const DURATION_THRESHOLD_UNITS = offered(DURATION_UNITS, ['us', 'ms', 's']);

/** Allocation and native-memory sampling intervals. */
export const BYTE_INTERVAL_UNITS = offered(BYTE_UNITS, ['kb', 'mb']);

/** The row for a unit, whatever case it was stored in, or undefined for one the table lacks. */
export function findUnit(table: readonly OptionUnit[], unit: string): OptionUnit | undefined {
  const normalized = unit.toLowerCase();
  return table.find(candidate => candidate.unit === normalized);
}

/** Whether a select offering these units can hold the stored value. */
export function isOffered(units: readonly OptionUnit[], unit: string): boolean {
  return units.some(candidate => candidate.unit === unit);
}

/** How the builder shows a unit; an unknown unit is shown as stored. */
export function unitLabel(unit: string): string {
  return (findUnit(DURATION_UNITS, unit) ?? findUnit(BYTE_UNITS, unit))?.label ?? unit;
}
