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
import TimeRange from "@/services/api/model/TimeRange";
import FlamegraphClient from "@/services/api/FlamegraphClient.ts";
import PrimaryFlamegraphClient from "@/services/api/PrimaryFlamegraphClient.ts";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";

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

        // First fetch only timeseries to know the data range
        this.httpClient.provideTimeseries(null)
            .then(timeseries => {
                // Initialize timeseries with full data (needed for brush chart)
                this.timeseriesOnInitCallback(timeseries);
                this.timeseriesOnUpdateFinishedCallback();

                // Calculate initial time range based on visibleMinutes setting
                const initialTimeRange = this.calculateInitialTimeRange(timeseries);

                // Fetch flamegraph with the calculated range (zoomed or full)
                this.httpClient.provide(initialTimeRange)
                    .then(flamegraph => {
                        this.flamegraphOnInitCallback(flamegraph);
                        this.flamegraphOnUpdateFinishedCallback();
                    });
            });
    }

    /**
     * Calculate the initial time range based on initialVisibleMinutes.
     * Returns null if full range should be used.
     * Note: Timeseries data is in seconds (relative to recording start).
     * Backend API expects milliseconds with absoluteTime=false for relative ranges.
     */
    private calculateInitialTimeRange(timeseries: TimeseriesData): TimeRange | null {
        if (this.initialVisibleMinutes === null) {
            return null;
        }

        if (!timeseries?.series?.length) {
            return null;
        }

        const series = timeseries.series[0];
        if (!series.data?.length) {
            return null;
        }

        // Timeseries data timestamps are in seconds
        const minTimeSeconds = series.data[0][0];
        const maxTimeSeconds = series.data[series.data.length - 1][0];
        const totalRangeSeconds = maxTimeSeconds - minTimeSeconds;
        const visibleRangeSeconds = this.initialVisibleMinutes * 60; // minutes to seconds

        // Only zoom if visible range is significantly smaller than total (less than 99%)
        if (visibleRangeSeconds >= totalRangeSeconds * 0.99) {
            return null;
        }

        // Convert to milliseconds for backend API (which expects milliseconds)
        // Use absoluteTime=false because this is relative to recording start
        return new TimeRange(
            Math.floor(minTimeSeconds * 1000),
            Math.ceil(Math.min(minTimeSeconds + visibleRangeSeconds, maxTimeSeconds) * 1000),
            false
        );
    }

    public updateWithZoom(timeRange: TimeRange): void {
        this.flamegraphOnUpdateStartedCallback();
        this.timeseriesOnZoomCallback();

        this.httpClient.provide(timeRange)
            .then(data => {
                this.flamegraphOnZoomCallback(data);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public resetZoom(): void {
        this.flamegraphOnUpdateStartedCallback();
        this.timeseriesOnResetZoomCallback();

        this.httpClient.provide(null)
            .then(data => {
                this.flamegraphOnResetZoomCallback(data);
                this.flamegraphOnUpdateFinishedCallback();
            });
    }

    public updateWithSearch(expression: string): void {
        this.flamegraphOnUpdateStartedCallback();
        this.searchBarOnUpdateStartedCallback();

        if (this.timeseriesSearchEnabled) {
            this.timeseriesOnUpdateStartedCallback();

            this.httpClient.provideTimeseries(expression)
                .then(data => {
                    this.flamegraphOnSearchCallback(expression);
                    this.timeseriesOnSearchCallback(data);

                    this.flamegraphOnUpdateFinishedCallback();
                    this.timeseriesOnUpdateFinishedCallback();
                    this.searchBarOnUpdateFinishedCallback();
                });
        } else {
            // Only search in flamegraph, no timeseries update
            this.flamegraphOnSearchCallback(expression);
            this.flamegraphOnUpdateFinishedCallback();
            this.searchBarOnUpdateFinishedCallback();
        }
    }

    public resetSearch(): void {
        this.flamegraphOnResetSearchCallback();
        this.timeseriesOnResetSearchCallback();
        this.searchBarOnMatchedCallback(null);
    }

    public updateModes(useThreadMode: boolean, useWeight: boolean): void {
        if (this.httpClient instanceof PrimaryFlamegraphClient) {
            this.httpClient.setUseThreadMode(useThreadMode);
            this.httpClient.setUseWeight(useWeight);
            this.initialize();
        }
    }
}
