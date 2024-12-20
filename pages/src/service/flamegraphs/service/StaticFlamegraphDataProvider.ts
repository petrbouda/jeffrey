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
import FlamegraphDataProvider from "@/service/flamegraphs/service/FlamegraphDataProvider";
import TimeseriesData from "@/service/timeseries/model/TimeseriesData";

export default class StaticFlamegraphDataProvider extends FlamegraphDataProvider {

    private readonly flamegraphData: FlamegraphData;
    private readonly timeseriesData: TimeseriesData;

    constructor(flamegraphData: FlamegraphData, timeseriesData: TimeseriesData) {
        super();
        this.flamegraphData = flamegraphData
        this.timeseriesData = timeseriesData;
    }

    provide(timeRange: any): Promise<FlamegraphData> {
        return Promise.resolve(this.flamegraphData);
    }

    provideTimeseries(search: string | null): Promise<TimeseriesData> {
        return Promise.resolve(this.timeseriesData);
    }

    export(timeRange: any): Promise<void> {
        console.error("Cannot export flamegraph from statically generated data")
        return Promise.resolve();
    }
}
