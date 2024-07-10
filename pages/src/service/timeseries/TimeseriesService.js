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
import ReplaceableToken from "@/service/replace/ReplaceableToken";
import CompressionUtils from "@/service/CompressionUtils";

export default class TimeseriesService {

    constructor(primaryProfileId, secondaryProfileId, eventType, useWeight, graphType, generated) {
        this.primaryProfileId = primaryProfileId;
        this.secondaryProfileId = secondaryProfileId;
        this.eventType = eventType;
        this.useWeight = useWeight;
        this.graphType = graphType;
        this.generated = generated;
    }

    generateWithSearch(search) {
        const content = {
            primaryProfileId: this.primaryProfileId,
            eventType: this.eventType,
            search: search,
            useWeight: this.useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete/search', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    generate() {
        if (this.generated) {
            return this.#generateStatic();
        }

        if (this.graphType === GraphType.PRIMARY) {
            return this.#generatePrimary()
        } else if (this.graphType === GraphType.DIFFERENTIAL) {
            return this.#generateDiff();
        } else {
            console.log("Unknown graph-type: " + this.graphType);
            return null
        }
    }

    #generatePrimary() {
        const content = {
            primaryProfileId: this.primaryProfileId,
            eventType: this.eventType,
            useWeight: this.useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    #generateDiff() {
        const content = {
            primaryProfileId: this.primaryProfileId,
            secondaryProfileId: this.secondaryProfileId,
            eventType: this.eventType,
            useWeight: this.useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    // Used for generated flamegraph (e.g. command-line tool)
    #generateStatic() {
        const data = CompressionUtils.decodeAndDecompress(ReplaceableToken.TIMESERIES)
        return Promise.resolve(JSON.parse(data))
    }
}
