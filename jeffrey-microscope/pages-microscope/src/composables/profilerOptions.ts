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
import { BYTE_UNITS, DURATION_UNITS, findUnit } from '@/composables/profilerUnits';
import type { OptionUnit } from '@/composables/profilerUnits';

/**
 * The threshold options of an async-profiler command: `trace=M:T`, `lock=T`, `alloc=N` and
 * `nativemem=N`.
 *
 * async-profiler reads every amount with `strtol` followed by a one-letter unit, so a fraction such
 * as `0.5ms` is not a smaller threshold but an invalid one: the profiler start fails for `trace`,
 * and `lock` silently turns lock profiling off. An amount is therefore always written as an
 * integer, converted to the largest unit that holds it exactly (`0.5ms` becomes `500us`).
 */

/** `lock=0` records every contention; a bare `lock` would mean async-profiler's 10 µs default. */
const EVERY_CONTENTION = '0';

const LATENCY_SEPARATOR = ':';
const DESCRIPTOR_START = '(';
const MEMBER_SEPARATOR = '.';
const WHITESPACE = /\s/;

const PATTERN_SHAPE_HINT =
  'Use Class.method, for example com.example.OrderService.place. ' +
  'Native methods such as Java_java_lang_Thread_start cannot be traced.';
const PATTERN_COLON_HINT =
  'Use Class.method without a colon: the latency threshold is set on the row, ' +
  'and JVM symbols such as G1CollectedHeap::allocate cannot be traced.';
const PATTERN_WHITESPACE_HINT = 'A method pattern cannot contain spaces.';

/**
 * A threshold as typed into the builder. A cleared number input hands Vue `''`, so anything that
 * is not a finite, non-negative number is read as "no threshold".
 */
export function normalizeThreshold(raw: unknown): number | null {
  return typeof raw === 'number' && Number.isFinite(raw) && raw >= 0 ? raw : null;
}

/** The amount in base units, or null when it is not a threshold: unset, zero, or under one base unit. */
function baseAmount(
  value: number | null,
  unit: string,
  units: readonly OptionUnit[]
): number | null {
  const normalized = normalizeThreshold(value);
  const scale = findUnit(units, unit)?.scale;
  if (normalized === null || scale === undefined) {
    return null;
  }
  const amount = Math.round(normalized * scale);
  return amount > 0 ? amount : null;
}

/** The amount as async-profiler reads it: an integer in the largest unit that holds it exactly. */
function exactAmount(amount: number, units: readonly OptionUnit[]): string {
  const unit = units.find(candidate => amount % candidate.scale === 0) ?? units[units.length - 1];
  return `${amount / unit.scale}${unit.suffix}`;
}

/** A duration for the NANOS table (`5ms`, `500us`), or null when there is no threshold. */
export function durationAmount(value: number | null, unit: string): string | null {
  const nanos = baseAmount(value, unit, DURATION_UNITS);
  return nanos === null ? null : exactAmount(nanos, DURATION_UNITS);
}

/** A size for the BYTES table (`512k`, `4m`), or null when there is no threshold. */
export function byteAmount(value: number | null, unit: string): string | null {
  const bytes = baseAmount(value, unit, BYTE_UNITS);
  return bytes === null ? null : exactAmount(bytes, BYTE_UNITS);
}

/**
 * The `trace=` option for one target. The latency is appended only when it is a threshold:
 * async-profiler treats a missing latency as 0, i.e. every call is recorded.
 */
export function traceOption(target: MethodTraceTarget): string {
  const pattern = target.pattern.trim();
  const latency = durationAmount(target.latencyValue, target.latencyUnit);
  return latency === null ? `trace=${pattern}` : `trace=${pattern}${LATENCY_SEPARATOR}${latency}`;
}

/**
 * The `lock=` option. It is always written with a value: a bare `lock` means async-profiler's
 * 10 µs default, so an empty field is written as `lock=0`, every contention, which is what the
 * builder shows for it.
 */
export function lockOption(value: number | null, unit: string): string {
  return `lock=${durationAmount(value, unit) ?? EVERY_CONTENTION}`;
}

/** The `alloc` option: one sample per N bytes allocated, or a bare `alloc` for the default. */
export function allocOption(value: number | null, unit: string): string {
  const interval = byteAmount(value, unit);
  return interval === null ? 'alloc' : `alloc=${interval}`;
}

/** The `nativemem` option: one sample per N bytes, or a bare `nativemem` recording every malloc. */
export function nativeMemOption(value: number | null, unit: string): string {
  const interval = byteAmount(value, unit);
  return interval === null ? 'nativemem' : `nativemem=${interval}`;
}

/**
 * Why async-profiler would reject a `trace=` target, or null when it has the `Class.method` shape.
 * One bad target fails the whole profiler start (`instrument.cpp` `addTarget`), so the builder
 * refuses it up front instead of leaving the JVM to run unprofiled.
 */
export function tracePatternError(pattern: string): string | null {
  const trimmed = pattern.trim();
  if (trimmed.length === 0) {
    return PATTERN_SHAPE_HINT;
  }
  if (WHITESPACE.test(trimmed)) {
    return PATTERN_WHITESPACE_HINT;
  }
  if (trimmed.includes(LATENCY_SEPARATOR)) {
    return PATTERN_COLON_HINT;
  }
  const descriptorStart = trimmed.indexOf(DESCRIPTOR_START);
  const qualifiedName = descriptorStart === -1 ? trimmed : trimmed.slice(0, descriptorStart);
  const separator = qualifiedName.lastIndexOf(MEMBER_SEPARATOR);
  const hasClass = separator > 0;
  const hasMethod = separator < qualifiedName.length - 1;
  if (!hasClass || !hasMethod) {
    return PATTERN_SHAPE_HINT;
  }
  return null;
}
