/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import RecordingSession from "@/services/api/model/RecordingSession.ts";
import RepositoryFile from "@/services/api/model/RepositoryFile.ts";
import RepositoryStatistics from "@/services/api/model/RepositoryStatistics.ts";

export default class ProjectRepositoryClient {
    private baseUrl: string;

    constructor(workspaceId: string, projectId: string) {
        this.baseUrl = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/repository'
    }

    listRecordingSessions(): Promise<RecordingSession[]> {
        return axios.get<RecordingSession[]>(this.baseUrl + '/sessions', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    getRepositoryStatistics(): Promise<RepositoryStatistics> {
        return axios.get<RepositoryStatistics>(this.baseUrl + '/statistics', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    copyRecordingSession(recordingSession: RecordingSession, merge: boolean): Promise<void> {
        const content = {
            sessionId: recordingSession.id,
            merge: merge,
        }

        return axios.post<void>(this.baseUrl + '/sessions/download', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    deleteRecordingSession(recordingSession: RecordingSession): Promise<void> {
        return axios.delete<void>(this.baseUrl + '/sessions/' + recordingSession.id)
            .then(HttpUtils.RETURN_DATA);
    }

    copySelectedRepositoryFile(sessionId: string, files: RepositoryFile[], merge: boolean): Promise<void> {
        const ids: string[] = files.map(it => it.id)
        const content = {
            sessionId: sessionId,
            recordingIds: ids,
            merge: merge,
        }

        return axios.post<void>(this.baseUrl + '/recordings/download', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    deleteSelectedRepositoryFile(sessionId: string, files: RepositoryFile[]): Promise<void> {
        const ids: string[] = files.map(it => it.id)
        const content = {
            sessionId: sessionId,
            recordingIds: ids,
        }

        return axios.post<void>(this.baseUrl + '/recordings/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

}
