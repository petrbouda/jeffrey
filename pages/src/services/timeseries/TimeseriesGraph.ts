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

import Flamegraph from "@/services/flamegraphs/Flamegraph";
import TimeseriesEventAxeFormatter from "@/services/timeseries/TimeseriesEventAxeFormatter";
import ApexCharts from "apexcharts"
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import Zoom from "@/services/timeseries/Zoom";
import Serie from "@/services/timeseries/model/Serie";

export default class TimeseriesGraph {

    valueFormatter: (n: number) => string
    chart: ApexCharts | null = null
    element: HTMLElement
    originalSeries: Serie[] | null = null
    currentZoom: Zoom | null = null
    useWeight = false
    zoomCallback : (minX: number, maxX: number) => void
    stacked: boolean
    zoomEnabled: boolean

    constructor(eventType: string, elementId: string, zoomCallback: (minX: number, maxX: number) => void, stacked: boolean, useWeight: boolean, zoomEnabled: boolean) {
        this.useWeight = useWeight
        this.zoomEnabled = zoomEnabled;
        if (useWeight) {
            this.valueFormatter = TimeseriesEventAxeFormatter.resolveFormatter(eventType)
        } else {
            this.valueFormatter = (value) =>  value + ""
        }
        this.zoomCallback = zoomCallback
        this.stacked = stacked
        this.element = document.querySelector('#' + elementId)!!;
        this.chart = null;
        this.originalSeries = null
    }

    render(timeseriesData: TimeseriesData) {
        this.chart = new ApexCharts(this.element, this.#options(timeseriesData.series, this.stacked, this.zoomCallback, this.resolveGraphTypeValue(timeseriesData)));
        this.originalSeries = timeseriesData.series
        this.chart.render();
    }

    private resolveGraphTypeValue(data: TimeseriesData) {
        const series = data.series[0]
        const firstValue = series.data[0]
        const lastValue = series.data[series.data.length - 1]
        // returns Bar if the series is shorter than 10 minute
        const diffInSecond = lastValue[0] - firstValue[0]
        return diffInSecond > 600 ? 'Area' : 'Bar'
    }

    search(timeseriesData: TimeseriesData) {
        this.chart!.updateSeries(timeseriesData.series, false)
    }

    resetSearch() {
        this.chart!.updateSeries(this.originalSeries!, false)

        if (this.currentZoom != null) {
            this.chart!.zoomX(this.currentZoom.min, this.currentZoom.max)
        }
    }

    resetZoom() {
        this.chart!.resetSeries()
        this.currentZoom = null
    }

    changeGraphType(type: string) {
        if (type === "Bar") {
            this.chart!.updateOptions({
                chart: {
                    type: type.toLowerCase()
                },
                fill: {
                    type: "solid"
                }
            })
        } else {
            this.chart!.updateOptions({
                chart: {
                    type: type.toLowerCase()
                },
                fill: {
                    type: 'gradient',
                    gradient: {
                        opacityFrom: 0.3,
                        opacityTo: 0.5,
                    }
                }
            })
        }
    }

    #options(series, stacked: boolean, zoomCallback: (minX: number, maxX: number) => void, graphType: string) {
        return {
            chart: {
                selection: {
                    background: '#90CAF9',
                    border: '#0D47A1'
                },
                animations: {
                    enabled: false
                },
                type: graphType.toLowerCase(),
                height: 300,
                stacked: stacked,
                zoom: {
                    type: "x",
                    enabled: this.zoomEnabled,
                    allowMouseWheelZoom: false
                },
                toolbar: {
                    show: false,
                    autoSelected: "zoom"
                },
                events: {
                    zoomed: (chartContext, {xaxis, yaxis}) => {
                        this.currentZoom = new Zoom(xaxis.min, xaxis.max)
                        zoomCallback(xaxis.min, xaxis.max)
                    }
                }
            },
            stroke: {
                curve: "smooth",
                width: 1
            },
            dataLabels: {
                enabled: false
            },
            colors: ['#0000ff', Flamegraph.HIGHLIGHTED_COLOR],
            fill: {
                type: 'gradient',
                gradient: {
                    opacityFrom: 0.3,
                    opacityTo: 0.5,
                }
            },
            series: series,
            xaxis: {
                type: "datetime",
                tooltip: {
                    enabled: false
                }
            },
            yaxis: {
                tooltip: {
                    enabled: false
                },
                labels: {
                    formatter: this.valueFormatter
                }
            },
            tooltip: {
                x: {
                    show: true,
                    format: 'MMM dd HH:mm:ss',
                },
            }
        };
    }
}
