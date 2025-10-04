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

import GlobalVars from "@/services/GlobalVars";
import axios from "axios";
import HttpUtils from "@/services/HttpUtils";
import FlamegraphData from "@/services/flamegraphs/model/FlamegraphData";
import FlamegraphClient from "@/services/flamegraphs/client/FlamegraphClient";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import Serie from "@/services/timeseries/model/Serie";
import GraphComponents from "@/services/flamegraphs/model/GraphComponents";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import BothGraphData from "@/services/flamegraphs/model/BothGraphData";

export default class GuardianFlamegraphClient extends FlamegraphClient {

    private readonly baseUrlFlamegraph: string;
    private readonly baseUrlTimeseries: string;
    private readonly eventType: string;
    private readonly useWeight: boolean;
    private readonly markers: any;

    constructor(
        workspaceId: string,
        projectId: string,
        profileId: string,
        eventType: string,
        useWeight: boolean,
        markers: any) {

        super();
        this.baseUrlFlamegraph = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/profiles/' + profileId + '/flamegraph'
        this.baseUrlTimeseries = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/profiles/' + profileId + '/timeseries'
        this.eventType = eventType;
        this.useWeight = useWeight;
        this.markers = markers;
    }

    provideBoth(components: GraphComponents, timeRange: TimeRange | null, search: string | null): Promise<BothGraphData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            markers: this.markers,
            useThreadMode: false,
            timeRange: timeRange,
            search: search,
            excludeNonJavaSamples: false,
            excludeIdleSamples: false,
            onlyUnsafeAllocationSamples: false,
            threadInfo: null,
            components: components,
        };

        return axios.post<BothGraphData>(this.baseUrlFlamegraph, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
    }

    provide(timeRange: any): Promise<FlamegraphData> {
        const content = {
            eventType: this.eventType,
            timeRange: timeRange,
            useWeight: this.useWeight,
            markers: this.markers
        };

        return axios.post<FlamegraphData>(this.baseUrlFlamegraph, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
    }

    provideTimeseries(search: string | null): Promise<TimeseriesData>{
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            markers: this.markers,
            search: search,
        };

        return axios.post<Serie[]>(this.baseUrlTimeseries, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA)
            .then(series => new TimeseriesData(series))
    }

    save(components: GraphComponents, flamegraphName: string, timeRange: TimeRange | null): Promise<void> {
        const content = {
            flamegraphName: flamegraphName,
            eventType: this.eventType,
            timeRange: timeRange,
            useWeight: this.useWeight,
            markers: this.markers,
            components: components
        };

        return axios.post<void>(this.baseUrlFlamegraph + '/repository', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
