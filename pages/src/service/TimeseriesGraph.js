export default class TimeseriesGraph {

    chart = null
    element = null

    constructor(elementId, series, selectedFn) {
        this.element = document.querySelector('#' + elementId);
        this.chart = new ApexCharts(this.element, this.#options(series, selectedFn));
    }

    render() {
        this.chart.render();
    }

    update(series) {
        this.chart.updateSeries(series, false)
    }

    resetZoom() {
        this.chart.resetSeries()
    }

    #options(series, selectedFn) {
        return {
            chart: {
                animations: {
                    enabled: false
                },
                type: "bar",
                height: 250,
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
                    fillColors: ['#0000ff', '#505050']
                }
            },
            fill: {
                colors: ['#0000ff', '#505050']
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
