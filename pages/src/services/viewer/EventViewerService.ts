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
import EventType from "@/services/viewer/model/EventType.ts";
import EventFieldDescription from "@/services/viewer/model/EventFieldDescription.ts";
import EventTypeDescription from "@/services/viewer/model/EventTypeDescription.ts";

export default class EventViewerService {

    private baseUrl: string;

    constructor(projectId: string, profileId: string) {
        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/viewer';
    }

    eventTypes(): Promise<EventTypeDescription[]> {
        return axios.get<EventTypeDescription[]>(
            this.baseUrl + '/events/types', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    eventTypesTree(): Promise<EventType[]> {
        return axios.get<EventType[]>(
            this.baseUrl + '/events/types/tree', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    events(eventType: string): Promise<Record<string, string | number>[]> {
        return axios.get<Record<string, string | number>[]>(
            this.baseUrl + '/events/' + eventType, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    eventColumns(eventType: string): Promise<EventFieldDescription[]> {
        return axios.get<EventFieldDescription[]>(
            this.baseUrl + '/events/' + eventType + '/columns', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
