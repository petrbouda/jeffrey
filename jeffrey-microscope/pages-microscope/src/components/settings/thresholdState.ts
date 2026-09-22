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

import type { MethodTraceTarget } from '@/types/profiler';
import { byteAmount, durationAmount } from '@/composables/profilerOptions';
import { unitLabel } from '@/composables/profilerUnits';

/**
 * What a threshold field makes async-profiler do, in words. `recordsEverything` marks the
 * expensive state -- no threshold at all -- so the builder can show it rather than leave it
 * implied by an empty field.
 */
export interface ThresholdState {
  label: string;
  recordsEverything: boolean;
}

/**
 * Whether a duration field is a threshold in the command. The check is the one the emitter makes,
 * so the badge never claims a threshold that the command does not carry.
 */
function hasDuration(value: number | null, unit: string): boolean {
  return durationAmount(value, unit) !== null;
}

function hasSize(value: number | null, unit: string): boolean {
  return byteAmount(value, unit) !== null;
}

/** `trace=M:T` keeps calls of at least T; without T every call is recorded. */
export function traceThreshold(target: MethodTraceTarget): ThresholdState {
  if (hasDuration(target.latencyValue, target.latencyUnit)) {
    return {
      label: `≥ ${target.latencyValue} ${unitLabel(target.latencyUnit)}`,
      recordsEverything: false
    };
  }
  return { label: 'every call', recordsEverything: true };
}

/** `lock=T` keeps contentions that waited at least T; `lock=0` keeps every one. */
export function lockThreshold(value: number | null, unit: string): ThresholdState {
  if (hasDuration(value, unit)) {
    return { label: `waits ≥ ${value} ${unitLabel(unit)}`, recordsEverything: false };
  }
  return { label: 'every contention', recordsEverything: true };
}

/** `nativemem=N` takes one sample per N bytes allocated; a bare `nativemem` records every malloc. */
export function nativeMemThreshold(value: number | null, unit: string): ThresholdState {
  if (hasSize(value, unit)) {
    return { label: `one sample per ${value} ${unitLabel(unit)}`, recordsEverything: false };
  }
  return { label: 'every malloc', recordsEverything: true };
}
