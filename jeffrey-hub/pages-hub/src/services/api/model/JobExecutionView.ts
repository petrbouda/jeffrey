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

import type { ExecutionLevel } from '@/services/api/model/JobView';

export type JobExecutionStatus = 'SUCCESS' | 'FAILURE';

/**
 * One entry of the in-memory scheduler job execution history
 * (GET /api/internal/scheduler/executions). Timestamps are UTC epoch millis.
 */
export interface JobExecutionView {
    jobType: string;
    executionLevel: ExecutionLevel;
    startedAt: number;
    durationMs: number;
    status: JobExecutionStatus;
    noop: boolean;
    summary: string | null;
    items: string[];
    error: string | null;
}
