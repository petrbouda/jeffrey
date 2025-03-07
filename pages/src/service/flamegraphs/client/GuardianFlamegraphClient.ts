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
import Serie from "@/service/timeseries/model/Serie";

export default class GuardianFlamegraphClient extends FlamegraphClient {

    private readonly baseUrlFlamegraph: string;
    private readonly baseUrlTimeseries: string;
    private readonly eventType: string;
    private readonly useWeight: boolean;
    private readonly markers: any;

    constructor(
        projectId: string,
        profileId: string,
        eventType: string,
        useWeight: boolean,
        markers: any) {

        super();
        this.baseUrlFlamegraph = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/flamegraph'
        this.baseUrlTimeseries = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/timeseries'
        this.eventType = eventType;
        this.useWeight = useWeight;
        this.markers = markers;
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

    export(timeRange: any): Promise<void> {
        const content = {
            eventType: this.eventType,
            timeRange: timeRange,
            useWeight: this.useWeight,
            markers: this.markers,
        };

        return axios.post<void>(this.baseUrlFlamegraph + '/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
