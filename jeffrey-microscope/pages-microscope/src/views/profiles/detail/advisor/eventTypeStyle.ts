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
 * A colour + icon for each analysis type, so the Advisor's cards and timeline read at a glance. Keyed by
 * the primary JFR event code (stable, and what both the cards and the per-type progress carry). Colours
 * come from the app's own semantic tokens — CPU warms, time cools, memory grows, contention alarms — so
 * the accent stays on-brand while giving each type its own identity.
 */
export interface EventTypeStyle {
  icon: string;
  color: string;
  light: string;
}

const BY_CODE: Record<string, EventTypeStyle> = {
  'jdk.ExecutionSample': { icon: 'bi-cpu', color: 'var(--color-warning)', light: 'var(--color-warning-light)' },
  'profiler.WallClockSample': { icon: 'bi-stopwatch', color: 'var(--color-info)', light: 'var(--color-info-light)' },
  'jdk.ObjectAllocationSample': { icon: 'bi-stack', color: 'var(--color-success)', light: 'var(--color-success-light)' },
  'jdk.JavaMonitorEnter': { icon: 'bi-lock', color: 'var(--color-danger)', light: 'var(--color-danger-light)' }
};

const DEFAULT_STYLE: EventTypeStyle = {
  icon: 'bi-activity',
  color: 'var(--color-primary)',
  light: 'var(--color-primary-light)'
};

const DESCRIPTIONS: Record<string, string> = {
  'jdk.ExecutionSample': 'Where CPU cycles are spent',
  'profiler.WallClockSample': 'Elapsed time, waiting included',
  'jdk.ObjectAllocationSample': 'Where memory is allocated',
  'jdk.JavaMonitorEnter': 'Time parked on locks'
};


/**
 * The order the Advisor lists its event types in — CPU, Wall-Clock, Allocation, Blocking — mirroring the
 * backend's `AdvisorPromptType` declaration order. Prompts, Recommendations and Patches all sort by it, so
 * a type keeps its position as you move between the three pages instead of shuffling per page.
 */
const CANONICAL_ORDER: string[] = [
  'jdk.ExecutionSample',
  'profiler.WallClockSample',
  'jdk.ObjectAllocationSample',
  'jdk.JavaMonitorEnter'
];

/** Unknown codes keep to the end rather than pushing a known type out of its place. */
function orderIndex(eventTypeCode: string): number {
  const index = CANONICAL_ORDER.indexOf(eventTypeCode);
  return index === -1 ? CANONICAL_ORDER.length : index;
}

/** Comparator putting event types in the Advisor's canonical order; unknown ones last, alphabetically. */
export function compareEventTypes(left: string, right: string): number {
  const byOrder = orderIndex(left) - orderIndex(right);
  if (byOrder !== 0) {
    return byOrder;
  }
  return left.localeCompare(right);
}

/**
 * The profiler option that captures each type — the same flags the Profiler Settings command builder
 * writes. A type missing from a recording is only a useful thing to say next to what would have
 * produced it, which is what a card shows in place of its data.
 */
const CAPTURE_FLAGS: Record<string, string> = {
  'jdk.ExecutionSample': 'event=ctimer',
  'profiler.WallClockSample': 'wall=10ms',
  'jdk.ObjectAllocationSample': 'alloc=512k',
  'jdk.JavaMonitorEnter': 'lock=10ms'
};

export function eventTypeCaptureFlag(eventTypeCode: string): string {
  return CAPTURE_FLAGS[eventTypeCode] ?? '';
}

/** How many trailing segments of a frame name a card shows before it stops being readable. */
const METHOD_SEGMENTS_SHOWN = 2;

/**
 * A frame name shortened to its class and method — `com.acme.pricing.PricingEngine.applyRules` reads as
 * `PricingEngine.applyRules`. Cards have room for the part that identifies the code and none for the
 * package; the full name goes in the element's title.
 */
export function shortMethodName(method: string): string {
  const segments = method.split('.');
  if (segments.length <= METHOD_SEGMENTS_SHOWN) {
    return method;
  }
  return segments.slice(-METHOD_SEGMENTS_SHOWN).join('.');
}

export function eventTypeStyle(eventTypeCode: string): EventTypeStyle {
  return BY_CODE[eventTypeCode] ?? DEFAULT_STYLE;
}

/** A one-line "what this measures" for a type's card, or empty when unknown. */
export function eventTypeDescription(eventTypeCode: string): string {
  return DESCRIPTIONS[eventTypeCode] ?? '';
}

/** The `--et` / `--et-light` custom properties a card or tile binds so its accent follows the type. */
export function eventTypeVars(eventTypeCode: string): Record<string, string> {
  const style = eventTypeStyle(eventTypeCode);
  return { '--et': style.color, '--et-light': style.light };
}
