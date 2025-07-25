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
import Project from "@/services/model/Project.ts";
import ProjectTemplateInfo from "@/services/project/model/ProjectTemplateInfo.ts";
import TemplateTarget from "@/services/model/TemplateTarget.ts";

export default class ProjectsClient {

    private static baseUrl = GlobalVars.url + '/projects';

    static async list(): Promise<Project[]> {
        return axios.get<Project[]>(ProjectsClient.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    // We don't need a get method as we can use the list method and find the project by ID

    static async create(name: string, templateId?: string) {
        const content = templateId
            ? {name: name, templateId: templateId}
            : {name: name};
        return axios.post(ProjectsClient.baseUrl, content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static async templates(target?: TemplateTarget): Promise<ProjectTemplateInfo[]> {
        const url = ProjectsClient.baseUrl + '/templates';

        return axios.get<ProjectTemplateInfo[]>(url, {
            headers: {Accept: 'application/json'},
            params: {target: target},
        }).then(HttpUtils.RETURN_DATA);
    }
}
