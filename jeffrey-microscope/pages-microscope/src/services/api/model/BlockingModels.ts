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

export interface BlockingOverview {
  contendedMonitorCount: number;
  totalMonitorBlockedNanos: number;
  waitCount: number;
  parkCount: number;
  sleepCount: number;
  pinnedCount: number;
  hasMonitorEnter: boolean;
  hasMonitorWaits: boolean;
  hasParks: boolean;
  hasSleeps: boolean;
  hasPinned: boolean;
}

export interface ContentionStat {
  className: string;
  count: number;
  totalNanos: number;
  maxNanos: number;
  threadCount: number;
}

export interface PinnedThreadEntry {
  thread: string | null;
  durationNanos: number;
}

export interface MonitorWaitStat {
  className: string;
  count: number;
  totalNanos: number;
  maxNanos: number;
  threadCount: number;
  timedOutCount: number;
}

export interface SleepStat {
  thread: string;
  count: number;
  totalSleptNanos: number;
  maxSleptNanos: number;
  requestedNanos: number;
}
