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
import GraphType from "@/service/flamegraphs/GraphType";
import CompressionUtils from "@/service/CompressionUtils";
import ReplaceableToken from "@/service/replace/ReplaceableToken";

export default class FlamegraphService {

    constructor(primaryProfileId, secondaryProfileId, eventType, useThreadMode, useWeight, graphType, generated) {
        this.primaryProfileId = primaryProfileId;
        this.secondaryProfileId = secondaryProfileId;
        this.eventType = eventType;
        this.useThreadMode = useThreadMode;
        this.useWeight = useWeight;
        this.graphType = graphType;
        this.generated = generated;
    }

    supportedEvents() {
        const content = {
            profileId: this.primaryProfileId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/events', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    supportedEventsDiff() {
        const content = {
            primaryProfileId: this.primaryProfileId,
            secondaryProfileId: this.secondaryProfileId
        };

        return axios.post(GlobalVars.url + '/flamegraph/events/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    generate(timeRage) {
        if (this.generated) {
            return this.#generateStatic();
        }

        if (this.graphType === GraphType.PRIMARY) {
            return this.#generatePrimary(timeRage)
        } else if (this.graphType === GraphType.DIFFERENTIAL) {
            return this.#generateDiff(timeRage);
        } else {
            console.log("Unknown graph-type: " + this.graphType);
            return null
        }
    }

    #generatePrimary(timeRange) {
        const content = {
            primaryProfileId: this.primaryProfileId,
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    #generateDiff(timeRange) {
        const content = {
            primaryProfileId: this.primaryProfileId,
            secondaryProfileId: this.secondaryProfileId,
            timeRange: timeRange,
            eventType: this.eventType,
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

     // Used for generated flamegraph (e.g. command-line tool)
     #generateStatic() {
        const data = CompressionUtils.decodeAndDecompress(ReplaceableToken.FLAMEGRAPH)
        return Promise.resolve(JSON.parse(data))
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

    export(timeRange) {
        if (this.graphType === GraphType.PRIMARY) {
            return this.#exportPrimary(timeRange)
        } else if (this.graphType === GraphType.DIFFERENTIAL) {
            return this.#exportDiff(timeRange);
        } else {
            console.log("Unknown graph-type for exporting flamegraph: " + this.graphType);
            return null
        }
    }

    #exportPrimary(timeRange) {
        const content = {
            primaryProfileId: this.primaryProfileId,
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode
        };

        return axios.post(GlobalVars.url + '/flamegraph/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    #exportDiff(timeRange) {
        const content = {
            primaryProfileId: this.primaryProfileId,
            secondaryProfileId: this.secondaryProfileId,
            timeRange: timeRange,
            eventType: this.eventType,
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
