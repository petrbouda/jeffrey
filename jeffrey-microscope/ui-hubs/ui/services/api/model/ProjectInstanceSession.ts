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

import MissingHeartbeat from '@hubs/services/api/model/MissingHeartbeat';

export default class ProjectInstanceSession {
  constructor(
    public id: string,
    public repositoryId: string,
    public createdAt: number,
    public duration: number,
    public finishedAt?: number,
    public isActive?: boolean,
    /** Finished without producing any data (zero bytes) — e.g. a crash-looped container. */
    public failed?: boolean,
    /** Set only for a session the hub finished because no heartbeat ever arrived. */
    public missingHeartbeat: MissingHeartbeat | null = null
  ) {}
}
