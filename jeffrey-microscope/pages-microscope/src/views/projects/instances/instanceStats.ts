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

import type ProjectInstance from '@hubs/services/api/model/ProjectInstance';
import type { ProjectInstanceStatus } from '@hubs/services/api/model/ProjectInstance';

/** A project's instances reduced to the figures the tab-row summary shows. */
export interface InstanceStats {
  pending: number;
  active: number;
  finished: number;
  expired: number;
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
    expired: countOf('EXPIRED'),
    total: instances.length,
    sessions: instances.reduce((sum, instance) => sum + instance.sessionCount, 0),
    oldestCreatedAt:
      instances.length === 0 ? null : Math.min(...instances.map(instance => instance.createdAt))
  };
}
