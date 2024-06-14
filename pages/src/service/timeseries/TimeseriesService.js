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

export default class TimeseriesService {
    static generate(primaryProfileId, eventType, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateWithSearch(primaryProfileId, eventType, search, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType,
            search: search,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete/search', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateDiff(primaryProfileId, secondaryProfileId, eventType, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            eventType: eventType,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
