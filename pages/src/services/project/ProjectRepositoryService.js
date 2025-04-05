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

export default class ProjectRepositoryService {

    constructor(projectId) {
        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/repository'
    }

    create(repositoryPath, repositoryType, createIfNotExists) {
        const content = {
            repositoryPath: repositoryPath,
            repositoryType: repositoryType,
            createIfNotExists: createIfNotExists
        };

        return axios.post(this.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    get() {
        return axios.get(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete() {
        return axios.delete(this.baseUrl)
            .then(HttpUtils.RETURN_DATA);
    }

    generateRecording() {
        return axios.post(this.baseUrl + '/generate', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
