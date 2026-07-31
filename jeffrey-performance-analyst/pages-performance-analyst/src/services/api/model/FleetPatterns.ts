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

/** One project's encounter with a recurring hotspot. */
export interface FleetOccurrence {
  hubId: string;
  workspaceId: string;
  projectId: string;
  projectName: string | null;
  recordingId: string;
  eventType: string;
  sourcePath: string | null;
  selfPct: number;
}

/** A hotspot that costs more than one project time, with the occurrences behind it. */
export interface FleetPattern {
  citedFrame: string;
  projectCount: number;
  occurrenceCount: number;
  peakSelfPct: number;
  occurrences: FleetOccurrence[];
}

/**
 * The fleet rollup. `analyzedProjectCount` is the denominator behind "affects 7 projects" — without
 * it the number has no scale.
 */
export default interface FleetPatterns {
  patterns: FleetPattern[];
  analyzedProjectCount: number;
}
