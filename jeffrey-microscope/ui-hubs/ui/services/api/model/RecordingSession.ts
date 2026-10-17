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

import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';
import RepositoryFile from '@hubs/services/api/model/RepositoryFile.ts';

export default class RecordingSession {
  constructor(
    public id: string,
    public name: string,
    public instanceId: string,
    public createdAt: number,
    public finishedAt: number | null,
    public status: RecordingStatus,
    public duration: number,
    /** Empty in a listing; filled only when the session is read on its own. */
    public files: RepositoryFile[],
    public retained: boolean = false,
    /**
     * The hub finished this session because no heartbeat arrived within its startup grace.
     * Such a session is FINISHED and usually empty, yet it is not a failed session.
     */
    public heartbeatMissing: boolean = false,
    /** How many files the session holds; a listing sends this instead of the files. */
    public fileCount: number = 0,
    /** Bytes the session's files occupy; a listing sends this instead of the files. */
    public sizeBytes: number = 0
  ) {}
}
