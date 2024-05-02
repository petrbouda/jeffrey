import Flamegraph from "@/service/Flamegraph";
import FormattingService from "@/service/FormattingService";

export default class TimeseriesGraph {

    static LABEL_FORMATTERS = new Map();

    static {
        TimeseriesGraph.LABEL_FORMATTERS.set(Flamegraph.EVENTS_MODE, (value) => { return value })
        TimeseriesGraph.LABEL_FORMATTERS.set(Flamegraph.WEIGHT_MODE, (value) => { return FormattingService.formatBytes(value) })
    }

    chart = null
    element = null
    originalSeries = null
    currentValueMode = Flamegraph.EVENTS_MODE

    constructor(elementId, series, selectedFn, stacked, valueMode) {
        this.currentValueMode = valueMode
        this.element = document.querySelector('#' + elementId);
        this.chart = new ApexCharts(this.element, this.#options(series, stacked, selectedFn));
        this.originalSeries = series
    }

    render() {
        this.chart.render();
    }

    setValueMode(valueMode) {
        this.currentValueMode = valueMode
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
                    formatter: TimeseriesGraph.LABEL_FORMATTERS.get(this.currentValueMode)
                }
            },
        })
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
                },
                labels: {
                    formatter: TimeseriesGraph.LABEL_FORMATTERS.get(this.currentValueMode)
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
