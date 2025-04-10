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

import FlamegraphData from "@/services/flamegraphs/model/FlamegraphData";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import FlamegraphClient from "@/services/flamegraphs/client/FlamegraphClient";

export default abstract class GraphUpdater {

    protected flamegraphRegistered: boolean = false;
    protected timeseriesRegistered: boolean = false;
    protected httpClient: FlamegraphClient;
    private readonly immediateInitialization: boolean = false;

    protected constructor(httpClient: FlamegraphClient, immediateInitialization: boolean) {
        this.httpClient = httpClient;
        this.immediateInitialization = immediateInitialization;
    }

    protected flamegraphOnUpdateStartedCallback: () => void = () => {
    };

    protected flamegraphOnUpdateFinishedCallback: () => void = () => {
    };

    protected flamegraphOnInitCallback: (data: FlamegraphData, timeRange: TimeRange | null) => void = () => {
    };

    protected flamegraphOnSearchCallback: (data: string) => void = () => {
    };

    protected flamegraphOnResetSearchCallback: () => void = () => {
    };

    protected flamegraphOnZoomCallback: (data: FlamegraphData, timeRange: TimeRange) => void = () => {
    };

    protected flamegraphOnResetZoomCallback: (data: FlamegraphData, timeRange: TimeRange | null) => void = () => {
    };

    protected timeseriesOnUpdateStartedCallback: () => void = () => {
    };

    protected timeseriesOnUpdateFinishedCallback: () => void = () => {
    };

    protected timeseriesOnInitCallback: (data: TimeseriesData) => void = () => {
    };

    protected timeseriesOnSearchCallback: (data: TimeseriesData) => void = () => {
    };

    protected timeseriesOnResetSearchCallback: () => void = () => {
    };

    protected timeseriesOnZoomCallback: (data: void) => void = () => {
    };

    protected timeseriesOnResetZoomCallback: () => void = () => {
    };

    public registerFlamegraphCallbacks(
        onUpdateStarted: () => void,
        onUpdateFinished: () => void,
        onInit: (data: FlamegraphData, timeRange: TimeRange | null) => void,
        onSearch: (data: string) => void,
        onResetSearch: () => void,
        onZoom: (data: FlamegraphData, timeRange: TimeRange) => void,
        onResetZoom: (data: FlamegraphData, timeRange: TimeRange | null) => void
    ): void {

        this.flamegraphOnUpdateStartedCallback = onUpdateStarted;
        this.flamegraphOnUpdateFinishedCallback = onUpdateFinished;
        this.flamegraphOnInitCallback = onInit;
        this.flamegraphOnSearchCallback = onSearch;
        this.flamegraphOnResetSearchCallback = onResetSearch
        this.flamegraphOnZoomCallback = onZoom;
        this.flamegraphOnResetZoomCallback = onResetZoom;

        this.flamegraphRegistered = true;
        if (this.immediateInitialization) {
            this.initialize();
        }
    }

    public registerTimeseriesCallbacks(
        onUpdateStarted: () => void,
        onUpdateFinished: () => void,
        onInit: (data: TimeseriesData) => void,
        onSearch: (data: TimeseriesData) => void,
        onResetSearch: () => void,
        onZoom: (data: void) => void,
        onResetZoom: () => void
    ): void {

        this.timeseriesOnUpdateStartedCallback = onUpdateStarted;
        this.timeseriesOnUpdateFinishedCallback = onUpdateFinished;
        this.timeseriesOnInitCallback = onInit;
        this.timeseriesOnSearchCallback = onSearch;
        this.timeseriesOnResetSearchCallback = onResetSearch;
        this.timeseriesOnZoomCallback = onZoom;
        this.timeseriesOnResetZoomCallback = onResetZoom;

        this.timeseriesRegistered = true;
        if (this.immediateInitialization) {
            this.initialize();
        }
    }

    abstract initialize(): void

    abstract updateWithZoom(timeRange: TimeRange): void

    abstract resetZoom(): void

    abstract updateWithSearch(expression: string): void

    abstract resetSearch(): void

    public flamegraphClient(): FlamegraphClient {
        return this.httpClient
    }
}
