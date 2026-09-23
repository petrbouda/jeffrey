/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import type ProjectInstanceSession from '@hubs/services/api/model/ProjectInstanceSession';

export type ProjectInstanceStatus = 'PENDING' | 'ACTIVE' | 'FINISHED' | 'EXPIRED';

export default class ProjectInstance {
  constructor(
    public id: string,
    public instanceName: string,
    public projectId: string,
    public status: ProjectInstanceStatus,
    public createdAt: number,
    public duration: number,
    public sessionCount: number,
    public activeSessionId?: string,
    public finishedAt?: number,
    public expiringAt?: number,
    public expiredAt?: number,
    public sessions?: ProjectInstanceSession[]
  ) {}
}
