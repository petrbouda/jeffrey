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

import Flamegraph from "@/service/flamegraphs/Flamegraph";
import TimeseriesEventAxeFormatter from "@/service/timeseries/TimeseriesEventAxeFormatter";

export default class TimeseriesGraph {

    valueFormatter = null
    chart = null
    element = null
    originalSeries = null
    currentZoom = null
    useWeight = false

    constructor(eventType, elementId, series, zoomCallback, stacked, useWeight) {
        this.useWeight = useWeight
        if (useWeight) {
            this.valueFormatter = TimeseriesEventAxeFormatter.resolveFormatter(eventType)
        } else {
            this.valueFormatter = (value) => {
                return value
            }
        }
        this.element = document.querySelector('#' + elementId);
        this.chart = new ApexCharts(this.element, this.#options(series, stacked, zoomCallback));
        this.originalSeries = series
    }

    render() {
        this.chart.render();
    }

    update(series, stacked) {
        this.originalSeries = series

        this.chart.updateOptions({
            chart: {
                stacked: stacked
            },
            series: series,
            yaxis: {
                tooltip: {
                    enabled: false
                },
                labels: {
                    formatter: this.valueFormatter
                }
            },
        })
    }

    search(series) {
        this.chart.updateSeries(series, false)
    }

    resetSearch() {
        this.chart.updateSeries(this.originalSeries, false)

        if (this.currentZoom != null) {
            this.chart.zoomX(this.currentZoom.min, this.currentZoom.max)
        }
    }

    resetZoom() {
        this.chart.resetSeries(true, true)
        this.currentZoom = null
    }

    changeGraphType(type) {
        if (type === "Bar") {
            this.chart.updateOptions({
                chart: {
                    type: type.toLowerCase()
                },
                fill: {
                    type: "solid"
                }
            })
        } else {
            this.chart.updateOptions({
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

    #options(series, stacked, zoomCallback) {
        return {
            chart: {
                animations: {
                    enabled: false
                },
                type: "area",
                height: 300,
                stacked: stacked,
                zoom: {
                    type: "x",
                    enabled: true
                },
                toolbar: {
                    show: false,
                    autoSelected: "zoom"
                },
                events: {
                    zoomed: (chartContext, {xaxis, yaxis}) => {
                        this.currentZoom = {min: xaxis.min, max: xaxis.max}
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
