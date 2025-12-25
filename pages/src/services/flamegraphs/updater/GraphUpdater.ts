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

import FlamegraphData from "@/services/api/model/FlamegraphData";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import TimeRange from "@/services/api/model/TimeRange";
import FlamegraphClient from "@/services/api/FlamegraphClient";

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

    protected flamegraphOnInitCallback: (data: FlamegraphData) => void = () => {
    };

    protected flamegraphOnSearchCallback: (data: string) => void = () => {
    };

    protected flamegraphOnResetSearchCallback: () => void = () => {
    };

    protected flamegraphOnZoomCallback: (data: FlamegraphData) => void = () => {
    };

    protected flamegraphOnResetZoomCallback: (data: FlamegraphData) => void = () => {
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

    protected timeseriesControlResetZoomCallback: () => void = () => {
    };

    protected searchBarOnMatchedCallback: (matched: string | null) => void = () => {
    };

    protected searchBarOnUpdateStartedCallback: () => void = () => {
    };

    protected searchBarOnUpdateFinishedCallback: () => void = () => {
    };

    protected timeseriesSearchEnabled: boolean = true;
    protected initialVisibleMinutes: number | null = null;

    public setTimeseriesSearchEnabled(enabled: boolean): void {
        this.timeseriesSearchEnabled = enabled;
    }

    public setInitialVisibleMinutes(minutes: number): void {
        this.initialVisibleMinutes = minutes;
    }

    public registerSearchBarCallbacks(
        onUpdateStarted: () => void,
        onUpdateFinished: () => void,
        onMatched: (matched: string | null) => void
    ): void {
        this.searchBarOnUpdateStartedCallback = onUpdateStarted;
        this.searchBarOnUpdateFinishedCallback = onUpdateFinished;
        this.searchBarOnMatchedCallback = onMatched;
    }

    public reportMatched(matched: string | null): void {
        this.searchBarOnMatchedCallback(matched);
    }

    public registerTimeseriesControlCallbacks(
        onResetZoom: () => void
    ): void {
        this.timeseriesControlResetZoomCallback = onResetZoom;
    }

    public resetTimeseriesZoom(): void {
        this.timeseriesControlResetZoomCallback();
        this.resetZoom();
    }

    public registerFlamegraphCallbacks(
        onUpdateStarted: () => void,
        onUpdateFinished: () => void,
        onInit: (data: FlamegraphData) => void,
        onSearch: (data: string) => void,
        onResetSearch: () => void,
        onZoom: (data: FlamegraphData) => void,
        onResetZoom: (data: FlamegraphData) => void
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

    abstract updateModes(useThreadMode: boolean, useWeight: boolean): void
}
