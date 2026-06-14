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

import type { StwCategory, StwScope } from '@/services/api/model/stw/StwModels';

export interface StwLane {
  category: StwCategory;
  label: string;
  scope: StwScope;
  // Literal canvas/SVG fill colors (ApexCharts cannot resolve CSS var()); mirrors ThreadRow.ts.
  color: string;
}

/**
 * Lane order and presentation for the STW timeline — GLOBAL (whole-JVM) lanes first, then LOCAL
 * (per-thread) stalls. Keyed lookups avoid scattering category metadata across components.
 */
export const STW_LANES: StwLane[] = [
  { category: 'GC_PAUSE', label: 'GC Pause', scope: 'GLOBAL', color: 'rgb(245,166,35)' },
  { category: 'VM_OPERATION', label: 'VM Operation', scope: 'GLOBAL', color: 'rgb(228,87,46)' },
  { category: 'TIME_TO_SAFEPOINT', label: 'Time to Safepoint', scope: 'GLOBAL', color: 'rgb(142,68,173)' },
  { category: 'MONITOR', label: 'Monitor Contention', scope: 'LOCAL', color: 'rgb(236,204,116)' },
  { category: 'PARK', label: 'Thread Park', scope: 'LOCAL', color: 'rgb(134,173,225)' },
  { category: 'PINNED', label: 'VThread Pinned', scope: 'LOCAL', color: 'rgb(241,135,168)' }
];

const LANE_BY_CATEGORY: Record<StwCategory, StwLane> = STW_LANES.reduce(
  (acc, lane) => {
    acc[lane.category] = lane;
    return acc;
  },
  {} as Record<StwCategory, StwLane>
);

export function laneFor(category: StwCategory): StwLane {
  return LANE_BY_CATEGORY[category];
}
