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
import RepositoryInfo from "@/services/project/model/RepositoryInfo.ts";
import RecordingSession from "@/services/model/data/RecordingSession.ts";
import RepositoryFile from "@/services/model/data/RepositoryFile.ts";

export default class ProjectRepositoryClient {
    private baseUrl: string;

    constructor(projectId: string) {
        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/repository'
    }

    create(
        repositoryPath: string,
        repositoryType: string,
        createIfNotExists: boolean,
        finishedSessionDetectionFile: string | null) {
        const content = {
            repositoryPath: repositoryPath,
            repositoryType: repositoryType,
            createIfNotExists: createIfNotExists,
            finishedSessionDetectionFile: finishedSessionDetectionFile,
        };

        return axios.post(this.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    get(): Promise<RepositoryInfo> {
        return axios.get<RepositoryInfo>(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    listRecordingSessions(): Promise<RecordingSession[]> {
        return axios.get<RecordingSession[]>(this.baseUrl + '/data/sessions', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    copyRecordingSession(recordingSession: RecordingSession, merge: boolean): Promise<void> {
        const content = {
            id: recordingSession.id,
            merge: merge,
        }

        return axios.post<void>(this.baseUrl + '/data/sessions/copy', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    deleteRecordingSession(recordingSession: RecordingSession): Promise<void> {
        const content = {
            id: recordingSession.id,
        }

        return axios.put<void>(this.baseUrl + '/data/sessions', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    copySelectedRepositoryFile(sessionId: string, files: RepositoryFile[], merge: boolean): Promise<void> {
        const ids: string[] = files.map(it => it.id)
        const content = {
            sessionId: sessionId,
            recordingIds: ids,
            merge: merge,
        }

        return axios.post<void>(this.baseUrl + '/data/recordings/copy', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    deleteSelectedRepositoryFile(sessionId: string, files: RepositoryFile[]): Promise<void> {
        const ids: string[] = files.map(it => it.id)
        const content = {
            sessionId: sessionId,
            recordingIds: ids,
        }

        return axios.put<void>(this.baseUrl + '/data/recordings', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete(): Promise<void> {
        return axios.delete<void>(this.baseUrl)
            .then(HttpUtils.RETURN_DATA);
    }
}
