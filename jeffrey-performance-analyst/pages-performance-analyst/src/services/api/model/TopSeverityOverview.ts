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

import type Severity from '@/services/api/model/Severity';

/** One ranked recording on the Overview "Highest Impact" list. */
export interface TopSeverityRecommendation {
  recordingId: string;
  recordingName: string;
  hubId: string;
  workspaceId: string;
  projectId: string;
  projectName: string | null;
  severity: Severity;
  headline: string;
  generatedAt: number;
}

/** Per-severity recording counts for the Overview tiles. */
export interface SeverityCounts {
  critical: number;
  high: number;
  medium: number;
  low: number;
}

/** The Overview "Highest Impact" payload: tile counts + the ranked rows. */
export default interface TopSeverityOverview {
  counts: SeverityCounts;
  items: TopSeverityRecommendation[];
}
