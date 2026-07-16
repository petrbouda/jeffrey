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
 * Resolves chart colors from the design tokens in design-tokens.css so chart
 * components (ApexCharts options, donut series) never hardcode hex palettes.
 * Values are read once from the computed :root style and cached; fallbacks
 * keep charts rendering in non-DOM environments (unit tests, SSR).
 */

const SERIES_TOKEN_COUNT = 10;

const FALLBACKS: Record<string, string> = {
  'chart-series-1': '#2e93fa',
  'chart-series-2': '#66da26',
  'chart-series-3': '#546e7a',
  'chart-series-4': '#e91e63',
  'chart-series-5': '#ff9800',
  'chart-series-6': '#00d9e9',
  'chart-series-7': '#775dd0',
  'chart-series-8': '#f86624',
  'chart-series-9': '#9c27b0',
  'chart-series-10': '#1b998b',
  'chart-color-primary': '#2e93fa',
  'chart-color-secondary': '#e53935',
  'chart-color-highlight': '#8e44ad',
  'color-primary': '#5e64ff',
  'color-success': '#00d27a',
  'color-danger': '#e63757',
  'color-warning': '#f5803e',
  'color-info': '#39afd1',
  'color-white': '#ffffff',
  'color-border': '#eaedf1',
  'color-amber': '#f59e0b',
  'color-accent-blue': '#0d6efd',
  'color-purple': '#6f42c1',
  'color-text-muted': '#748194'
};

const cache = new Map<string, string>();

function resolveToken(tokenName: string): string {
  const cached = cache.get(tokenName);
  if (cached) {
    return cached;
  }

  let value = '';
  if (typeof document !== 'undefined') {
    value = getComputedStyle(document.documentElement).getPropertyValue(`--${tokenName}`).trim();
  }
  if (!value) {
    value = FALLBACKS[tokenName] ?? '#000000';
  }

  cache.set(tokenName, value);
  return value;
}

export default class ChartColors {
  /**
   * A single named chart color token, e.g. chartColor('primary') -> --chart-color-primary.
   * Also accepts any design token name, e.g. chartColor('color-danger') -> --color-danger.
   */
  static chartColor(name: 'primary' | 'secondary' | 'highlight' | string): string {
    if (name === 'primary' || name === 'secondary' || name === 'highlight') {
      return resolveToken(`chart-color-${name}`);
    }
    return resolveToken(name);
  }

  /**
   * The categorical series palette, cycled to the requested length.
   * chartPalette() returns all 10 base colors; chartPalette(n) returns n colors.
   */
  static chartPalette(count?: number): string[] {
    const base: string[] = [];
    for (let i = 1; i <= SERIES_TOKEN_COUNT; i++) {
      base.push(resolveToken(`chart-series-${i}`));
    }
    if (count === undefined || count <= SERIES_TOKEN_COUNT) {
      return count === undefined ? base : base.slice(0, Math.max(0, count));
    }

    const cycled: string[] = [];
    for (let i = 0; i < count; i++) {
      cycled.push(base[i % base.length]);
    }
    return cycled;
  }

  /** Color for series at an index, cycling through the palette. */
  static seriesColor(index: number): string {
    const normalized = ((index % SERIES_TOKEN_COUNT) + SERIES_TOKEN_COUNT) % SERIES_TOKEN_COUNT;
    return resolveToken(`chart-series-${normalized + 1}`);
  }
}
