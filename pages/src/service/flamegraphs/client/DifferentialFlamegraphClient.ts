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

import GlobalVars from "@/service/GlobalVars";
import axios from "axios";
import HttpUtils from "@/service/HttpUtils";
import FlamegraphData from "@/service/flamegraphs/model/FlamegraphData";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import TimeseriesData from "@/service/timeseries/model/TimeseriesData";
import TimeRange from "@/service/flamegraphs/model/TimeRange";
import BothGraphData from "@/service/flamegraphs/model/BothGraphData";
import GraphComponents from "@/service/flamegraphs/model/GraphComponents";

export default class DifferentialFlamegraphClient extends FlamegraphClient {

    private readonly baseUrlFlamegraph: string;
    private readonly eventType: string;
    private readonly useWeight: boolean;
    private readonly excludeNonJavaSamples: boolean;
    private readonly excludeIdleSamples: boolean;
    private readonly onlyUnsafeAllocationSamples: boolean;

    constructor(
        projectId: string,
        primaryProfileId: string,
        secondaryProfileId: string,
        eventType: string,
        useWeight: boolean,
        excludeNonJavaSamples: boolean,
        excludeIdleSamples: boolean,
        onlyUnsafeAllocationSamples: boolean) {

        super();
        this.baseUrlFlamegraph = GlobalVars.url + '/projects/' + projectId + '/profiles/' + primaryProfileId + '/diff/' + secondaryProfileId + '/differential-flamegraph'
        this.eventType = eventType;
        this.useWeight = useWeight;
        this.excludeNonJavaSamples = excludeNonJavaSamples;
        this.excludeIdleSamples = excludeIdleSamples;
        this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
    }

    // Differential Graph does not support Searching
    provideBoth(components: GraphComponents, timeRange: TimeRange | null, search: string | null): Promise<BothGraphData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            timeRange: timeRange,
            search: search,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            components: components,
        };

        return axios.post<BothGraphData>(this.baseUrlFlamegraph, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
    }

    provide(timeRange: any): Promise<FlamegraphData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            timeRange: timeRange,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            components: GraphComponents.FLAMEGRAPH_ONLY,
        };

        return axios.post<BothGraphData>(this.baseUrlFlamegraph, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
            .then(data => data.flamegraph);
    }

    provideTimeseries(_ignored: string | null): Promise<TimeseriesData> {
        return Promise.reject("Differential Flamegraph does not support searching")
    }

    save(components: GraphComponents, flamegraphName: string, timeRange: TimeRange | null): Promise<void> {
        const content = {
            flamegraphName: flamegraphName,
            eventType: this.eventType,
            timeRange: timeRange,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            components: components
        };

        return axios.post<void>(this.baseUrlFlamegraph + '/repository', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
