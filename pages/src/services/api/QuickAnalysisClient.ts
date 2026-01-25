/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import GlobalVars from '@/services/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import QuickAnalysisProfile from '@/services/api/model/QuickAnalysisProfile';

/**
 * Client for quick/ad-hoc JFR analysis.
 * Allows analyzing JFR files by uploading them to the server.
 */
export default class QuickAnalysisClient {

    private static baseUrl = GlobalVars.internalUrl + '/quick-analysis';

    /**
     * Upload and analyze a JFR file.
     * The file is uploaded, parsed, and a profile is created automatically.
     *
     * @param file The JFR file to upload and analyze
     * @returns The profile ID of the created profile
     */
    static async uploadAndAnalyze(file: File): Promise<string> {
        const formData = new FormData();
        formData.append('file', file);

        return axios.post<{ profileId: string }>(
            QuickAnalysisClient.baseUrl,
            formData,
            { headers: { 'Content-Type': 'multipart/form-data' } }
        ).then(response => response.data.profileId);
    }

    /**
     * Upload a heap dump file and create a profile for analysis.
     * The heap dump is saved and a lightweight profile is created without JFR parsing.
     *
     * @param file The heap dump file to upload (.hprof or .hprof.gz)
     * @returns The profile ID of the created profile
     */
    static async uploadHeapDump(file: File): Promise<string> {
        const formData = new FormData();
        formData.append('file', file);

        return axios.post<{ profileId: string }>(
            QuickAnalysisClient.baseUrl + '/heap-dump',
            formData,
            { headers: { 'Content-Type': 'multipart/form-data' } }
        ).then(response => response.data.profileId);
    }

    /**
     * List all quick analysis profiles.
     */
    static async listProfiles(): Promise<QuickAnalysisProfile[]> {
        return axios.get<QuickAnalysisProfile[]>(
            QuickAnalysisClient.baseUrl + '/profiles',
            HttpUtils.JSON_ACCEPT_HEADER
        ).then(HttpUtils.RETURN_DATA);
    }

    /**
     * Delete a quick analysis profile.
     */
    static async deleteProfile(profileId: string): Promise<void> {
        return axios.delete(
            QuickAnalysisClient.baseUrl + '/profiles/' + profileId,
            HttpUtils.JSON_ACCEPT_HEADER
        ).then(() => undefined);
    }
}
