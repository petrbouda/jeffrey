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

import FlamegraphData from "@/services/flamegraphs/model/FlamegraphData";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import BothGraphData from "@/services/flamegraphs/model/BothGraphData";
import GraphComponents from "@/services/flamegraphs/model/GraphComponents";

export default abstract class FlamegraphClient {

    abstract provideBoth(composition: GraphComponents, timeRange: TimeRange | null, search: string | null): Promise<BothGraphData>

    abstract provide(timeRange: TimeRange | null): Promise<FlamegraphData>

    abstract provideTimeseries(search: string | null): Promise<TimeseriesData>

    abstract save(components: GraphComponents, flamegraphName: string, timeRange: TimeRange | null): Promise<void>
}
