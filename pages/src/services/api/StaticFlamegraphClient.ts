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

import FlamegraphData from "@/services/api/model/FlamegraphData";
import FlamegraphClient from "@/services/api/FlamegraphClient";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import GraphComponents from "@/services/api/model/GraphComponents";
import TimeRange from "@/services/api/model/TimeRange";
import BothGraphData from "@/services/api/model/BothGraphData";

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
