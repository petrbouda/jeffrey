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

import FlamegraphData from "@/service/flamegraphs/model/FlamegraphData";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import TimeseriesData from "@/service/timeseries/model/TimeseriesData";
import GraphComponents from "@/service/flamegraphs/model/GraphComponents";
import TimeRange from "@/service/flamegraphs/model/TimeRange";
import BothGraphData from "@/service/flamegraphs/model/BothGraphData";

export default class StaticFlamegraphClient extends FlamegraphClient {

    private readonly flamegraphData: FlamegraphData;
    private readonly timeseriesData: TimeseriesData;

    constructor(bothGraphData: BothGraphData) {
        super();
        this.flamegraphData = bothGraphData.flamegraph
        this.timeseriesData = bothGraphData.timeseries;
    }

    provideBoth(composition: GraphComponents, timeRange: TimeRange | null, search: string | null): Promise<BothGraphData> {
        return Promise.resolve(new BothGraphData(this.flamegraphData, this.timeseriesData));
    }

    provide(timeRange: any): Promise<FlamegraphData> {
        return Promise.resolve(this.flamegraphData);
    }

    provideTimeseries(search: string | null): Promise<TimeseriesData> {
        return Promise.resolve(this.timeseriesData);
    }

    save(components: GraphComponents, flamegraphName: string, timeRange: TimeRange | null): Promise<void> {
        console.error("Cannot export flamegraph from statically generated data")
        return Promise.resolve();
    }
}
