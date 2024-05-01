import Flamegraph from "@/service/Flamegraph";

export default class TimeseriesGraph {

    chart = null
    element = null
    originalSeries = null

    constructor(elementId, series, selectedFn, stacked) {
        this.element = document.querySelector('#' + elementId);
        this.chart = new ApexCharts(this.element, this.#options(series, stacked, selectedFn));
        this.originalSeries = series
    }

    render() {
        this.chart.render();
    }

    update(series, stacked) {
        this.chart.updateOptions({
            chart: {
                stacked: stacked
            }
        })

        this.originalSeries = series
        this.chart.updateSeries(series, false)
    }

    search(series) {
        this.chart.updateSeries(series, false)
    }

    resetSearch() {
        this.chart.updateSeries(this.originalSeries, false)
    }

    resetZoom() {
        this.chart.resetSeries(true, true)
    }

    changeGraphType(type) {
        if (type === "bar") {
            this.chart.updateOptions({
                chart: {
                    type: type
                },
                fill: {
                    type: "solid"
                }
            })
        } else {
            this.chart.updateOptions({
                chart: {
                    type: type
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

    #options(series, stacked, selectedFn) {
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
                    zoomed: selectedFn
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
