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
import FlamegraphData from "@/services/api/model/FlamegraphData";
import FlamegraphClient from "@/services/api/FlamegraphClient";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import GraphComponents from "@/services/api/model/GraphComponents";
import TimeRange from "@/services/api/model/TimeRange";
import BothGraphData from "@/services/api/model/BothGraphData";
import ProtobufConverter from "@/services/flamegraphs/ProtobufConverter";

export default class GuardianFlamegraphClient extends FlamegraphClient {

    private readonly baseUrlFlamegraph: string;
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

        return axios.post<ArrayBuffer>(this.baseUrlFlamegraph, content, HttpUtils.PROTOBUF_HEADERS)
            .then(response => ProtobufConverter.decode(response.data));
    }

    provide(timeRange: TimeRange | null): Promise<FlamegraphData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            markers: this.markers,
            timeRange: timeRange,
            useThreadMode: false,
            excludeNonJavaSamples: false,
            excludeIdleSamples: false,
            onlyUnsafeAllocationSamples: false,
            threadInfo: null,
            components: GraphComponents.FLAMEGRAPH_ONLY,
        };

        return axios.post<ArrayBuffer>(this.baseUrlFlamegraph, content, HttpUtils.PROTOBUF_HEADERS)
            .then(response => ProtobufConverter.decode(response.data))
            .then(data => data.flamegraph);
    }

    provideTimeseries(search: string | null): Promise<TimeseriesData>{
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight,
            markers: this.markers,
            search: search,
            useThreadMode: false,
            excludeNonJavaSamples: false,
            excludeIdleSamples: false,
            onlyUnsafeAllocationSamples: false,
            threadInfo: null,
            components: GraphComponents.TIMESERIES_ONLY,
        };

        return axios.post<ArrayBuffer>(this.baseUrlFlamegraph, content, HttpUtils.PROTOBUF_HEADERS)
            .then(response => ProtobufConverter.decode(response.data))
            .then(data => data.timeseries);
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
