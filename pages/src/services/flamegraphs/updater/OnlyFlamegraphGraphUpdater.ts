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
import PrimaryFlamegraphClient from "@/services/flamegraphs/client/PrimaryFlamegraphClient";
import TimeRange from "@/services/flamegraphs/model/TimeRange";

export default class OnlyFlamegraphGraphUpdater extends GraphUpdater {

    private readonly timeRange: TimeRange | null;

    constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
        super(httpClient, immediateInitialization);
        this.timeRange = null;
    }

    public initialize(): void {
        this.flamegraphOnUpdateStartedCallback();
        this.httpClient.provide(this.timeRange)
            .then(data => {
                this.flamegraphOnInitCallback(data);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public updateWithZoom(timeRange: TimeRange): void {
        this.flamegraphOnUpdateStartedCallback();

        this.httpClient.provide(timeRange)
            .then(data => {
                this.flamegraphOnZoomCallback(data);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public resetZoom(): void {
        this.flamegraphOnUpdateStartedCallback();

        this.httpClient.provide(null)
            .then(data => {
                this.flamegraphOnResetZoomCallback(data);
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

    public updateModes(useThreadMode: boolean, useWeight: boolean): void {
        if (this.httpClient instanceof PrimaryFlamegraphClient) {
            this.httpClient.setUseThreadMode(useThreadMode);
            this.httpClient.setUseWeight(useWeight);
            this.initialize();
        }
    }
}
