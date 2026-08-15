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

import { contextColor, contextLabel } from '@/services/trace/traceLabels';

/**
 * Colours and sizes for the unified timeline.
 *
 * Canvas cannot read a CSS custom property, so the palette is resolved from the document at draw
 * time rather than written as literals — which is what keeps the timeline in step with the design
 * tokens and with both themes.
 */

/** Row geometry, in CSS pixels. */
export const TIMELINE_METRICS = {
  gutterWidth: 150,
  rulerHeight: 26,
  globalTrackHeight: 22,
  groupHeaderHeight: 18,
  threadRowHeight: 18,
  spanLaneHeight: 8,
  laneGap: 1,
  trackGap: 1,
  minimapHeight: 5
} as const;

/** Span-kind fills, matching the waterfall so a bar means the same thing on both screens. */
export const SPAN_KIND_TOKENS: Record<string, string> = {
  SERVER: '--flamegraph-color-blue',
  CLIENT: '--flamegraph-color-cyan',
  INTERNAL: '--flamegraph-color-green',
  PRODUCER: '--flamegraph-color-purple',
  CONSUMER: '--flamegraph-color-teal'
};

const FALLBACK_SPAN_TOKEN = '--flamegraph-color-green';

export function resolveToken(token: string, fallback: string): string {
  if (typeof document === 'undefined') {
    return fallback;
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim();
  return value.length > 0 ? value : fallback;
}

export function spanKindColor(kind: string): string {
  return resolveToken(SPAN_KIND_TOKENS[kind] ?? FALLBACK_SPAN_TOKEN, '#c9e7a5');
}

/**
 * The pause palette, taken from the trace views rather than redefined, so a GC pause is the same red
 * on the waterfall and on the timeline.
 */
export function pauseColor(category: string): string {
  const token = contextColor(category);
  const match = /var\((--[a-z0-9-]+)\)/i.exec(token);
  return match ? resolveToken(match[1], '#e63757') : token;
}

export function pauseLabel(category: string): string {
  return contextLabel(category);
}

/** Legend rows, resolved lazily so a theme switch repaints them correctly. */
export const TIMELINE_LEGEND = [
  { label: 'GC pause', color: 'var(--color-danger)' },
  { label: 'Safepoint', color: 'var(--color-warning)' },
  { label: 'Server span', color: 'var(--flamegraph-color-blue)' },
  { label: 'Client span', color: 'var(--flamegraph-color-cyan)' },
  { label: 'Internal span', color: 'var(--flamegraph-color-green)' },
  { label: 'Error span', color: 'var(--color-danger)' }
];
