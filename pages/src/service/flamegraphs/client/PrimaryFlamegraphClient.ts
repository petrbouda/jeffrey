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
import Serie from "@/service/timeseries/model/Serie";

export default class PrimaryFlamegraphClient extends FlamegraphClient {

    private readonly baseUrlFlamegraph: string;
    private readonly baseUrlTimeseries: string;
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
        this.baseUrlFlamegraph = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/flamegraph'
        this.baseUrlTimeseries = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/timeseries'
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

    provide(timeRange: any): Promise<FlamegraphData>{
        const content = {
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode,
            useWeight: this.useWeight,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            threadInfo: this.threadInfo,
        };

        return axios.post<FlamegraphData>(this.baseUrlFlamegraph, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
    }

    provideTimeseries(search: string | null): Promise<TimeseriesData>{
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            search: search,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
            threadInfo: this.threadInfo,
        };

        return axios.post<Serie[]>(this.baseUrlTimeseries, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
            .then(series => new TimeseriesData(series))
    }


    export(timeRange: any): Promise<void> {
        const content = {
            eventType: this.eventType,
            timeRange: timeRange,
            useThreadMode: this.useThreadMode,
            excludeNonJavaSamples: this.excludeNonJavaSamples,
            excludeIdleSamples: this.excludeIdleSamples,
            onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
        };

        return axios.post<void>(this.baseUrlFlamegraph + '/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
