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
 * A Guardian guard definition as returned by the platform API. matcherSpec and preconditions hold
 * JSON as text (see the MatchExpr / TraversalStrategy model in the backend).
 */
export default interface GuardianGuard {
  guardId: string;
  name: string;
  enabled: boolean;
  builtIn: boolean;
  groupKind: string;
  category: string;
  resultType: string;
  targetFrame: string;
  matchingType: string;
  infoThreshold: number;
  warningThreshold: number;
  matcherSpec: string;
  preconditions: string | null;
  summaryNoun: string | null;
  explanation: string | null;
  solution: string | null;
  createdAt: number | null;
}

/** The editable payload sent when creating or updating a guard (server assigns id/builtIn/createdAt). */
export type GuardianGuardRequest = Omit<GuardianGuard, 'guardId' | 'builtIn' | 'createdAt'>;
