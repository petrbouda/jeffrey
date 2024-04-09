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

    #options(series, stacked, selectedFn) {
        return {
            chart: {
                animations: {
                    enabled: false
                },
                type: "bar",
                height: 250,
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
            dataLabels: {
                enabled: false
            },
            legend: {
                markers: {
                    fillColors: ['#0000ff', Flamegraph.HIGHLIGHTED_COLOR]
                }
            },
            fill: {
                colors: ['#0000ff', Flamegraph.HIGHLIGHTED_COLOR]
            },
            series: series,
            xaxis: {
                type: "datetime"
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
