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

import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import FlamegraphClient from "@/services/flamegraphs/client/FlamegraphClient";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import GraphComponents from "@/services/flamegraphs/model/GraphComponents";

export default class FullGraphUpdater extends GraphUpdater {

    constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
        super(httpClient, immediateInitialization);
    }

    public initialize(): void {
        if (!this.flamegraphRegistered || !this.timeseriesRegistered) {
            return
        }

        this.flamegraphOnUpdateStartedCallback();
        this.timeseriesOnUpdateStartedCallback();

        this.httpClient.provideBoth(GraphComponents.BOTH, null, null)
            .then(data => {
                this.flamegraphOnInitCallback(data.flamegraph, null);
                this.timeseriesOnInitCallback(data.timeseries);

                this.flamegraphOnUpdateFinishedCallback();
                this.timeseriesOnUpdateFinishedCallback();
            });
    }

    public updateWithZoom(timeRange: TimeRange): void {
        this.flamegraphOnUpdateStartedCallback();
        this.timeseriesOnZoomCallback();

        this.httpClient.provide(timeRange)
            .then(data => {
                this.flamegraphOnZoomCallback(data, timeRange);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public resetZoom(): void {
        this.flamegraphOnUpdateStartedCallback();
        this.timeseriesOnResetZoomCallback();

        this.httpClient.provide(null)
            .then(data => {
                this.flamegraphOnResetZoomCallback(data, null);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public updateWithSearch(expression: string): void {
        this.flamegraphOnUpdateStartedCallback();
        this.timeseriesOnUpdateStartedCallback();

        this.httpClient.provideTimeseries(expression)
            .then(data => {
                this.flamegraphOnSearchCallback(expression);
                this.timeseriesOnSearchCallback(data);

                this.flamegraphOnUpdateFinishedCallback();
                this.timeseriesOnUpdateFinishedCallback();
            });
    }

    public resetSearch(): void {
        this.flamegraphOnResetSearchCallback();
        this.timeseriesOnResetSearchCallback();
    }
}
