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
 * The Advisor's concurrency ceiling, as the settings page presents it: four cards over one stored
 * number. Kept out of the component so the mapping between a card and the value it writes can be
 * read and tested on its own.
 */

/** The stored value meaning "no ceiling" — matches AdvisorParallelism.UNLIMITED on the backend. */
export const UNLIMITED = 0;

export const DEFAULT_MAX_CONCURRENT_RUNS = 2;

export type ParallelismKey = 'one' | 'balanced' | 'unlimited' | 'custom';

export interface ParallelismChoice {
  key: ParallelismKey;
  /** The value this card stores, or null for Custom, which keeps whatever the field holds. */
  value: number | null;
  badge: string;
  /** Whether the badge is a word rather than a figure, which needs different type treatment. */
  badgeIsWord: boolean;
  name: string;
  description: string;
  estimate: string;
  estIcon: string;
  risk: boolean;
}

/**
 * No card claims a fixed number of event types: a run analyzes only the types a recording actually
 * carries — one for a CPU-only recording — so the estimates are relative to each other rather than
 * to a count, and "every type at once" is a word rather than a figure.
 */
export const PARALLELISM_CHOICES: ParallelismChoice[] = [
  {
    key: 'one',
    value: 1,
    badge: '1',
    badgeIsWord: false,
    name: 'One at a time',
    description:
      'Each event type waits for the one before it. The gentlest option if your provider is rate-limited or shared with other tools.',
    estimate: 'Slowest — the sum of every type',
    estIcon: 'bi-clock',
    risk: false
  },
  {
    key: 'balanced',
    value: DEFAULT_MAX_CONCURRENT_RUNS,
    badge: String(DEFAULT_MAX_CONCURRENT_RUNS),
    badgeIsWord: false,
    name: 'Balanced',
    description:
      'The default. Two calls in flight halves the wall clock without crowding most provider limits.',
    estimate: 'About half of one-at-a-time',
    estIcon: 'bi-clock',
    risk: false
  },
  {
    key: 'unlimited',
    value: UNLIMITED,
    badge: 'All',
    badgeIsWord: true,
    name: 'Unlimited',
    description:
      'No ceiling — every event type a recording carries starts at once, so nothing ever queues.',
    estimate: 'Fastest — most likely to hit a rate limit',
    estIcon: 'bi-exclamation-triangle',
    risk: true
  },
  {
    key: 'custom',
    value: null,
    badge: '…',
    badgeIsWord: true,
    name: 'Custom',
    description:
      'A specific ceiling, for a self-hosted provider or an account with limits of its own.',
    estimate: 'Set a number below',
    estIcon: 'bi-pencil',
    risk: false
  }
];

/**
 * Which card a stored value selects. Anything that is not one of the named values is Custom — including
 * a value written by hand — so the field shows the real number rather than silently rounding it to a card.
 */
export function keyForValue(stored: string | number | undefined | null): ParallelismKey {
  const parsed = typeof stored === 'number' ? stored : Number.parseInt(String(stored ?? ''), 10);
  if (!Number.isFinite(parsed)) {
    return 'balanced';
  }
  if (parsed === UNLIMITED) {
    return 'unlimited';
  }
  if (parsed === 1) {
    return 'one';
  }
  if (parsed === DEFAULT_MAX_CONCURRENT_RUNS) {
    return 'balanced';
  }
  return 'custom';
}
