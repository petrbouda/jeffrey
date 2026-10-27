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
import RecordingEventSource from '@hubs/services/api/model/RecordingEventSource.ts';

export default class Profile {
  public deleting: boolean = false;

  // Present on the raw API payload of GET /profiles/{profileId} (epoch millis).
  public profilingStartedAt?: number;
  public profilingFinishedAt?: number;

  constructor(
    public id: string,
    public projectId: string,
    public workspaceId: string,
    public name: string,
    public createdAt: number,
    public eventSource: RecordingEventSource,
    public enabled: boolean,
    public modified: boolean,
    public durationInMillis: number,
    public sizeInBytes: number
  ) {}
}
