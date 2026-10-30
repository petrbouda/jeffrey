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

import axios from 'axios';
import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import HttpUtils from '@shared/services/HttpUtils';
import type RecordingGroup from '@remote/services/api/model/RecordingGroup';
import type Recording from '@remote/services/api/model/Recording';
import type Bundle from '@remote/services/api/model/Bundle';

/** What an import answers with: the recording's id and its event source, e.g. HEAP_DUMP. */
export interface ImportedRecording {
  recordingId: string;
  eventSource: string | null;
}

export default class RecordingsClient extends BasePlatformClient {
  constructor() {
    super('/recordings');
  }

  // --- Groups ---

  async createGroup(name: string): Promise<string> {
    return super.post<{ groupId: string }>('/groups', { name }).then(r => r.groupId);
  }

  async listGroups(): Promise<RecordingGroup[]> {
    return super.get<RecordingGroup[]>('/groups');
  }

  async deleteGroup(groupId: string): Promise<void> {
    return super.del<void>(`/groups/${groupId}`);
  }

  // --- Recordings ---

  async uploadRecording(file: File, groupId?: string): Promise<string> {
    const formData = new FormData();
    formData.append('file', file);
    if (groupId) {
      formData.append('groupId', groupId);
    }

    return axios
      .post<{
        recordingId: string;
      }>(this.baseUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      .then(response => response.data.recordingId);
  }

  /**
   * Imports a file from a path on the machine Microscope runs on. The answer carries the recording's
   * event source as well as its id, because the caller's next move is to analyse and open it, and
   * where a profile opens depends on what it is — a heap dump has no JFR dashboard to land on.
   */
  async importFromPath(path: string): Promise<ImportedRecording> {
    return super.post<ImportedRecording>('/from-path', { path }, { suppressToast: true });
  }

  async listRecordings(): Promise<Recording[]> {
    return super.get<Recording[]>('/recordings');
  }

  async moveRecordingToGroup(recordingId: string, groupId: string | null): Promise<void> {
    return super.put<void>(`/recordings/${recordingId}/group`, { groupId });
  }

  async deleteRecording(recordingId: string): Promise<void> {
    return super.del<void>(`/recordings/${recordingId}`);
  }

  async downloadFile(recordingId: string, fileId: string): Promise<void> {
    const downloadUrl =
      this.baseUrl +
      '/recordings/' +
      recordingId +
      '/files/' +
      encodeURIComponent(fileId) +
      '/download';
    return HttpUtils.downloadFile(downloadUrl, fileId);
  }

  async analyzeRecording(
    recordingId: string,
    options?: { suppressToast?: boolean }
  ): Promise<string> {
    return super
      .post<{ profileId: string }>(`/recordings/${recordingId}/analyze`, undefined, options)
      .then(r => r.profileId);
  }

  /** Builds another profile from the recording, beside the ones it already has. */
  async initializeAgain(recordingId: string): Promise<string> {
    return super
      .post<{ profileId: string }>(`/recordings/${recordingId}/profiles`, undefined)
      .then(r => r.profileId);
  }

  async updateProfileName(recordingId: string, profileId: string, name: string): Promise<void> {
    return super.put<void>(`/recordings/${recordingId}/profiles/${profileId}`, {
      name
    });
  }

  async deleteProfile(recordingId: string, profileId: string): Promise<void> {
    return super.del<void>(`/recordings/${recordingId}/profiles/${profileId}`);
  }

  // --- Bundles ---

  /** Every bundle with its download-only files; its items are recordings carrying its bundleId. */
  async listBundles(): Promise<Bundle[]> {
    return super.get<Bundle[]>('/bundles');
  }

  async moveBundleToGroup(bundleId: string, groupId: string | null): Promise<void> {
    return super.put<void>(`/bundles/${bundleId}/group`, { groupId });
  }

  /** Deletes the bundle with every item in it, their profiles, and its files. */
  async deleteBundle(bundleId: string): Promise<void> {
    return super.del<void>(`/bundles/${bundleId}`);
  }

  async downloadBundleFile(bundleId: string, fileId: string): Promise<void> {
    const downloadUrl =
      this.baseUrl + '/bundles/' + bundleId + '/files/' + encodeURIComponent(fileId) + '/download';
    return HttpUtils.downloadFile(downloadUrl, fileId);
  }

  async listProfiles(): Promise<Recording[]> {
    const recordings = await this.listRecordings();
    return recordings.filter(r => r.hasProfile && r.profileId);
  }
}
