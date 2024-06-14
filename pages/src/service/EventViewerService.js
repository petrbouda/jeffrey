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

export default class EventViewerService {

    static timeseries(primaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/viewer/events/timeseries', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static allEventTypes(primaryProfileId) {
        const content = {
            profileId: primaryProfileId
        };

        return axios.post(GlobalVars.url + '/viewer/all', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static events(primaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/viewer/events', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static eventColumns(primaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/viewer/events/columns', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
