/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import GraphUpdater from "@/service/flamegraphs/updater/GraphUpdater";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import TimeRange from "@/service/flamegraphs/model/TimeRange";
import GraphComponents from "@/service/flamegraphs/model/GraphComponents";

export default class OnlyFlamegraphPrimaryGraphUpdater extends GraphUpdater {

    private httpClient: FlamegraphClient;
    private timeRange: TimeRange;

    constructor(httpClient: FlamegraphClient, timeRange: TimeRange) {
        super();
        this.httpClient = httpClient;
        this.timeRange = timeRange;
    }

    public initialize(): void {
        this.flamegraphOnUpdateStartedCallback();

        this.httpClient.provideBoth(GraphComponents.FLAMEGRAPH_ONLY, this.timeRange, null)
            .then(data => {
                this.flamegraphOnInitCallback(data.flamegraph);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public updateWithZoom(timeRange: TimeRange): void {
        this.flamegraphOnUpdateStartedCallback();

        this.httpClient.provideBoth(GraphComponents.FLAMEGRAPH_ONLY, timeRange, null)
            .then(data => {
                this.flamegraphOnZoomCallback(data.flamegraph);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public resetZoom(): void {
        this.flamegraphOnUpdateStartedCallback();

        this.httpClient.provideBoth(GraphComponents.FLAMEGRAPH_ONLY, null, null)
            .then(data => {
                this.flamegraphOnResetZoomCallback(data.flamegraph);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public updateWithSearch(expression: string): void {
        this.flamegraphOnUpdateStartedCallback();
        this.flamegraphOnSearchCallback(expression);
        this.flamegraphOnUpdateFinishedCallback();
    }

    public resetSearch(): void {
        this.flamegraphOnResetSearchCallback();
    }
}
