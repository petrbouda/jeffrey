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
import ThreadInfo from "@/service/thread/model/ThreadInfo";
import TimeseriesData from "@/service/timeseries/model/TimeseriesData";
import BothGraphData from "@/service/flamegraphs/model/BothGraphData";
import TimeRange from "@/service/flamegraphs/model/TimeRange";
import GraphComponents from "@/service/flamegraphs/model/GraphComponents";

export default class PrimaryFlamegraphClient extends FlamegraphClient {

    private readonly baseUrl: string;
    private readonly eventType: string;
    private readonly useThreadMode: boolean;
    private readonly useWeight: boolean;
    private readonly excludeNonJavaSamples: boolean;
    private readonly excludeIdleSamples: boolean;
    private readonly onlyUnsafeAllocationSamples: boolean;
    private readonly threadInfo: ThreadInfo | null;

    constructor(
        projectId: string,
        profileId: string,
        eventType: string,
        useThreadMode: boolean,
        useWeight: boolean,
        excludeNonJavaSamples: boolean,
        excludeIdleSamples: boolean,
        onlyUnsafeAllocationSamples: boolean,
        threadInfo: ThreadInfo | null) {

        super();
        this.baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/flamegraph'
        this.eventType = eventType;
        this.useThreadMode = useThreadMode;
        this.useWeight = useWeight;
        this.excludeNonJavaSamples = excludeNonJavaSamples;
        this.excludeIdleSamples = excludeIdleSamples;
        this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
        this.threadInfo = threadInfo
    }

    static onlyEventType(projectId: string, profileId: string, eventType: string): PrimaryFlamegraphClient {
        return new PrimaryFlamegraphClient(
            projectId,
            profileId,
            eventType,
            false,
            false,
            false,
            false,
            false,
            null
        )
    }

    provideBoth(components: GraphComponents, timeRange: TimeRange | null, search: string | null): Promise<BothGraphData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            useThreadMode: this.useThreadMode,
            timeRange: timeRange,
            search: search,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            threadInfo: this.threadInfo,
            components: components,
        };

        return axios.post<BothGraphData>(this.baseUrl, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
    }

    provide(timeRange: TimeRange | null): Promise<FlamegraphData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            useThreadMode: this.useThreadMode,
            timeRange: timeRange,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            threadInfo: this.threadInfo,
            components: GraphComponents.FLAMEGRAPH_ONLY,
        };

        return axios.post<BothGraphData>(this.baseUrl, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
            .then(data => data.flamegraph);
    }

    provideTimeseries(search: string | null): Promise<TimeseriesData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            search: search,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            threadInfo: this.threadInfo,
            components: GraphComponents.TIMESERIES_ONLY,
        };

        return axios.post<TimeseriesData>(this.baseUrl, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
            .then(data => data.timeseries);
    }


    save(components: GraphComponents, flamegraphName: string, timeRange: TimeRange | null): Promise<void> {
        const content = {
            flamegraphName: flamegraphName,
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            components: components,
        };

        return axios.post<void>(this.baseUrl + '/repository', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
