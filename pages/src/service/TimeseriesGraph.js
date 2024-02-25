export default class TimeseriesGraph {

    chart = null
    element = null

    constructor(elementId, data, selectedFn) {
        this.element = document.querySelector('#' + elementId);
        this.chart = new ApexCharts(this.element,  this.#options(data, selectedFn));
    }

    render() {
        this.chart.render();
    }

    update(data) {
        this.chart.updateSeries([{ data: data }], false)
    }

    resetZoom() {
        this.chart.resetSeries()
    }

    #options(data, selectedFn) {
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
            series: [
                {
                    name: "Samples",
                    data: data
                }
            ],
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