/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import FormattingService from '@shared/services/FormattingService';
import type ProjectInstance from '@remote/services/api/model/ProjectInstance';
import type { ProjectInstanceStatus } from '@remote/services/api/model/ProjectInstance';
import type RepositoryStatistics from '@remote/services/api/model/RepositoryStatistics';

/** A project's instances reduced to the figures the tab-row summary shows. */
export interface InstanceStats {
  pending: number;
  active: number;
  finished: number;
  total: number;
  sessions: number;
  /** Creation time of the oldest instance (UTC epoch millis), null when there is none. */
  oldestCreatedAt: number | null;
}

export function summarizeInstances(instances: readonly ProjectInstance[]): InstanceStats {
  const countOf = (status: ProjectInstanceStatus) =>
    instances.filter(instance => instance.status === status).length;

  return {
    pending: countOf('PENDING'),
    active: countOf('ACTIVE'),
    finished: countOf('FINISHED'),
    total: instances.length,
    sessions: instances.reduce((sum, instance) => sum + instance.sessionCount, 0),
    oldestCreatedAt:
      instances.length === 0 ? null : Math.min(...instances.map(instance => instance.createdAt))
  };
}

/**
 * The project's storage size as the summary shows it. Only the hub's retention pass measures it,
 * so a project nothing has measured is "unmeasured", not empty.
 */
export type StorageFigure =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'unmeasured' }
  | { kind: 'measured'; value: string; unit: string };

export function storageFigure(
  statistics: RepositoryStatistics | null,
  failed: boolean
): StorageFigure {
  if (failed) {
    return { kind: 'error' };
  }
  if (statistics === null) {
    return { kind: 'loading' };
  }
  if (statistics.totalSize === null) {
    return { kind: 'unmeasured' };
  }
  return { kind: 'measured', ...FormattingService.formatBytesParts(statistics.totalSize) };
}
