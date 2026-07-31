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
import type PatchVerification from '@/services/api/model/PatchVerification';
import type Claim from '@/services/api/model/Claim';

/**
 * The stored artifacts of a recommendation run for one sample event type: the severity Jeffrey computed
 * from the measured profile, the recommendations markdown, an optional applicable patch (unified diff),
 * what was established about that patch, the checked claims, and what the run consumed. Returned by the
 * peek endpoint so a page load restores everything the original run showed.
 */
export default interface RecommendationArtifacts {
  eventType: string;
  severity: Severity;
  recommendations: string;
  patch: string | null;
  verification: PatchVerification | null;
  claims: Claim[];
  inputTokens: number;
  outputTokens: number;
  costUsd: number | null;
}
