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

import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class ProjectRecordingService {

    constructor(projectId) {
        this.projectId = projectId;
    }

    list() {
        return axios.get(GlobalVars.url + '/projects/' + this.projectId + '/recordings', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete(filePath) {
        const content = {
            filePaths: [filePath]
        };

        return axios.post(GlobalVars.url + '/projects/' + this.projectId + '/recordings/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
