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

    constructor(
        projectId,
        primaryProfileId,
        secondaryProfileId,
        eventType,
        useThreadMode,
        useWeight,
        graphType,
        excludeNonJavaSamples,
        excludeIdleSamples,
        markers,
        threadInfo,
        generated) {

        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles/' + primaryProfileId + '/flamegraph'
        this.diffBaseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles/' + primaryProfileId + '/diff/' + secondaryProfileId + '/differential-flamegraph'
        this.projectId = projectId;
        this.primaryProfileId = primaryProfileId;
        this.secondaryProfileId = secondaryProfileId;
        this.eventType = eventType;
        this.useThreadMode = useThreadMode;
        this.useWeight = useWeight;
        this.graphType = graphType;
        this.excludeNonJavaSamples = excludeNonJavaSamples;
        this.excludeIdleSamples = excludeIdleSamples;
        this.markers = markers;
        this.threadInfo = threadInfo;
        this.generated = generated;
    }

    supportedEvents() {
        return axios.get(this.baseUrl + '/events', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    supportedEventsDiff() {
        return axios.get(this.diffBaseUrl + '/events', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    generate(timeRage) {
        if (this.generated) {
            return this.#generateStatic();
        }
    }

    // Used for generated flamegraph (e.g. command-line tool)
    #generateStatic() {
        const data = CompressionUtils.decodeAndDecompress(ReplaceableToken.FLAMEGRAPH)
        return Promise.resolve(JSON.parse(data))
    }

    saveEventTypeRange(flamegraphName, timeRange) {
        if (this.graphType === GraphType.PRIMARY) {
            return this.#saveEventTypePrimaryRange(flamegraphName, timeRange)
        } else if (this.graphType === GraphType.DIFFERENTIAL) {
            return this.#saveEventTypeDiffRange(flamegraphName, timeRange);
        } else {
            console.log("Unknown graph-type: " + this.graphType);
            return null
        }
    }

    #saveEventTypePrimaryRange(flamegraphName, timeRange) {
        const content = {
            flamegraphName: flamegraphName,
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode,
            useWeight: this.useWeight,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            markers: this.markers
        };

        return axios.post(this.baseUrl + '/save', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    #saveEventTypeDiffRange(flamegraphName, timeRange) {
        const content = {
            flamegraphName: flamegraphName,
            timeRange: timeRange,
            eventType: this.eventType,
            useWeight: this.useWeight,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            markers: this.markers
        };

        return axios.post(this.diffBaseUrl + '/save', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    list() {
        return axios.get(this.baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    exportById(flamegraphId) {
        return axios.post(this.baseUrl + '/' + flamegraphId + '/export')
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
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode,
            useWeight: this.useWeight,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            markers: this.markers
        };

        return axios.post(this.baseUrl + '/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    #exportDiff(timeRange) {
        const content = {
            timeRange: timeRange,
            eventType: this.eventType,
            useWeight: this.useWeight,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            markers: this.markers
        };

        return axios.post(this.diffBaseUrl + '/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    getById(flamegraphId) {
        return axios.get(this.baseUrl + '/' + flamegraphId, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete(flamegraphId) {
        return axios.delete(this.baseUrl + '/' + flamegraphId);
    }
}
