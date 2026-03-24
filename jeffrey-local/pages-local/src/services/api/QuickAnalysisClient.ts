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

import axios from 'axios';
import BasePlatformClient from '@/services/api/BasePlatformClient';
import type QuickGroup from '@/services/api/model/QuickGroup';
import type QuickRecording from '@/services/api/model/QuickRecording';

export default class QuickAnalysisClient extends BasePlatformClient {

    constructor() {
        super('/quick-analysis');
    }

    // --- Groups ---

    async createGroup(name: string): Promise<string> {
        return super.post<{ groupId: string }>('/groups', { name })
            .then(r => r.groupId);
    }

    async listGroups(): Promise<QuickGroup[]> {
        return super.get<QuickGroup[]>('/groups');
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

        return axios.post<{ recordingId: string }>(
            this.baseUrl,
            formData,
            { headers: { 'Content-Type': 'multipart/form-data' } }
        ).then(response => response.data.recordingId);
    }

    async listRecordings(): Promise<QuickRecording[]> {
        return super.get<QuickRecording[]>('/recordings');
    }

    async moveRecordingToGroup(recordingId: string, groupId: string | null): Promise<void> {
        return super.put<void>(`/recordings/${recordingId}/group`, { groupId });
    }

    async deleteRecording(recordingId: string): Promise<void> {
        return super.del<void>(`/recordings/${recordingId}`);
    }

    async analyzeRecording(recordingId: string): Promise<string> {
        return super.post<{ profileId: string }>(`/recordings/${recordingId}/analyze`)
            .then(r => r.profileId);
    }

    async updateProfileName(recordingId: string, name: string): Promise<void> {
        return super.put<void>(`/recordings/${recordingId}/profile`, { name });
    }

    async deleteProfile(recordingId: string): Promise<void> {
        return super.del<void>(`/recordings/${recordingId}/profile`);
    }

    async listProfiles(): Promise<QuickRecording[]> {
        const recordings = await this.listRecordings();
        return recordings.filter(r => r.hasProfile && r.profileId);
    }
}
