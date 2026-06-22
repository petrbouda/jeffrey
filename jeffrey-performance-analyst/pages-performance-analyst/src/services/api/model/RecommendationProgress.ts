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

export type RecommendationStatus = 'CLONING' | 'ANALYZING' | 'COMPLETED' | 'FAILED';

/**
 * A progress snapshot for a repository-aware AI recommendation task, streamed from the backend over
 * SSE. `severity`, `recommendations` and `patch` are present only on the terminal COMPLETED event
 * (`patch` stays null when the model proposed no code edit); `errorMessage` only on FAILED.
 */
export default interface RecommendationProgress {
  taskId: string;
  recordingId: string;
  eventType: string;
  status: RecommendationStatus;
  message: string;
  severity: Severity | null;
  recommendations: string | null;
  patch: string | null;
  errorMessage: string | null;
}
