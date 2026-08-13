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
 * The geometry and grouping behind the events-in-a-window timeline.
 *
 * Kept out of the component because this is where the arithmetic mistakes live -- an empty window
 * dividing by zero, a marker escaping its lane, a bar scaled against the wrong denominator -- and
 * none of them need a DOM to demonstrate.
 */

/**
 * Lane colours, assigned by how many events a type contributed rather than by the type itself. The
 * busiest lane is always the same colour, which is what makes two spans comparable at a glance; the
 * cost is that a given event type is not stably coloured across spans.
 */
export const TYPE_COLORS = [
  '--flamegraph-color-blue',
  '--flamegraph-color-green',
  '--flamegraph-color-orange',
  '--flamegraph-color-purple',
  '--flamegraph-color-cyan',
  '--flamegraph-color-pink',
  '--flamegraph-color-peach',
  '--flamegraph-color-teal',
  '--flamegraph-color-red'
];

export interface TypedEvent {
  eventType: string;
}

export interface EventType {
  type: string;
  color: string;
}

export interface TypeBreakdownRow extends EventType {
  count: number;
  /** Width of the row's bar, relative to the busiest type in the window. */
  pct: number;
}

/** Types present in the events, busiest first, each with its lane colour. */
export function orderTypesByCount(events: TypedEvent[]): EventType[] {
  const counts = new Map<string, number>();
  for (const event of events) {
    counts.set(event.eventType, (counts.get(event.eventType) ?? 0) + 1);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([type], index) => ({ type, color: TYPE_COLORS[index % TYPE_COLORS.length] }));
}

/**
 * Where a value sits inside `[from, to]`, as a percentage. Clamped, so an event just outside the
 * selected window pins to an edge instead of escaping the track; a zero-length window puts
 * everything at the left edge rather than dividing by zero.
 */
export function positionPercent(value: number, from: number, to: number): number {
  const span = to - from || 1;
  return Math.min(100, Math.max(0, ((value - from) / span) * 100));
}

/** One line per whole second inside the window, as percentages across it. */
export function gridLinePercents(from: number, to: number): number[] {
  const span = to - from || 1;
  const lines: number[] = [];
  for (let second = Math.ceil(from / 1000) * 1000; second < to; second += 1000) {
    lines.push(((second - from) / span) * 100);
  }
  return lines;
}

/** Evenly spaced axis ticks across the window, already labelled. */
export function axisTicks(
  from: number,
  to: number,
  count: number
): { pos: number; label: string }[] {
  const span = to - from || 1;
  const ticks: { pos: number; label: string }[] = [];
  for (let i = 0; i < count; i++) {
    const at = from + (span * i) / (count - 1);
    ticks.push({ pos: (i / (count - 1)) * 100, label: compactMillis(at) });
  }
  return ticks;
}

/**
 * Per-type counts for the window, in lane order. Bars are scaled to the busiest type rather than to
 * the total, so the shape stays readable when one type dwarfs the rest -- which is the normal case,
 * sampling events being far more numerous than anything else.
 *
 * A type with nothing in the window still gets a row: its absence is information, and a legend that
 * reshuffles as you drag the brush is unreadable.
 */
export function breakdownOf(eventsInView: TypedEvent[], types: EventType[]): TypeBreakdownRow[] {
  const counts = new Map<string, number>();
  for (const event of eventsInView) {
    counts.set(event.eventType, (counts.get(event.eventType) ?? 0) + 1);
  }
  const max = Math.max(1, ...types.map(type => counts.get(type.type) ?? 0));
  return types.map(type => {
    const count = counts.get(type.type) ?? 0;
    return { type: type.type, color: type.color, count, pct: (count / max) * 100 };
  });
}

/** Axis and range labels: short enough to sit under a tick. */
export function compactMillis(millis: number): string {
  return millis >= 1000 ? (millis / 1000).toFixed(2) + 's' : Math.round(millis) + 'ms';
}

/** The event's own fields, cut to something a table cell can hold; the full text goes in a title. */
export function truncateFields(fields: string | null, max: number): string {
  if (!fields) {
    return '';
  }
  return fields.length > max ? fields.slice(0, max) + '…' : fields;
}
