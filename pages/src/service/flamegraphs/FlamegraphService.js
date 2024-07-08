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

export default class FlamegraphService {

    static supportedEvents(profileId) {
        const content = {
            profileId: profileId
        };

        return axios.post(GlobalVars.url + '/flamegraph/events', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static supportedEventsDiff(primaryProfileId, secondaryProfileId) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId
        };

        return axios.post(GlobalVars.url + '/flamegraph/events/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generate(profileId, eventType, threadModeEnabled, timeRange) {
        // const content = {
        //     primaryProfileId: profileId,
        //     eventType: eventType,
        //     timeRange: timeRange,
        //     useThreadMode: threadModeEnabled
        // };
        //
        // return axios.post(GlobalVars.url + '/flamegraph/generate', content, HttpUtils.JSON_HEADERS)
        //     .then(HttpUtils.RETURN_DATA);
        return Promise.resolve()
    }

    static generateDiff(primaryProfileId, secondaryProfileId, eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            timeRange: timeRange,
            eventType: eventType,
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static saveEventTypeRange(primaryProfileId, flamegraphName, eventType, timeRange, useThreadMode, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            flamegraphName: flamegraphName,
            eventType: eventType,
            timeRange: timeRange,
            useThreadMode: useThreadMode,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/flamegraph/save/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static saveEventTypeDiffRange(primaryProfileId, secondaryProfileId, flamegraphName, eventType, timeRange, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            flamegraphName: flamegraphName,
            timeRange: timeRange,
            eventType: eventType,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/flamegraph/save/diff/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static list(profileId) {
        const content = {
            profileId: profileId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/all', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportById(profileId, flamegraphId) {
        const content = {
            primaryProfileId: profileId,
            flamegraphId: flamegraphId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/id', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static export(profileId, eventType, timeRange, threadModeEnabled) {
        const content = {
            primaryProfileId: profileId,
            eventType: eventType,
            timeRange: timeRange,
            useThreadMode: threadModeEnabled
        };

        return axios.post(GlobalVars.url + '/flamegraph/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportDiff(primaryProfileId, secondaryProfileId, eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            timeRange: timeRange,
            eventType: eventType,
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static getById(profileId, flamegraphId) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/id', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static delete(profileId, flamegraphId) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId
        };

        return axios.post(GlobalVars.url + '/flamegraph/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER);
    }
}
