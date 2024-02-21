<script setup>
import { onMounted } from 'vue';

let chart;
let selectedRange = null;

onMounted(() => {
    var data = generateDayWiseTimeSeries(new Date('22 Apr 2017').getTime(), 360, {
        min: 0,
        max: 30
    });

    var options2 = {
        plotOptions: {
            bar: {}
        },
        dataLabels: {
            enabled: false
        },
        chart: {
            events: {
                click: function(event, chartContext, config) {
                    console.log(JSON.stringify(selectedRange));
                },
                selection: function(chartContext, { xaxis, yaxis }) {
                    selectedRange = xaxis;
                }
            },
            toolbar: {
                show: false,
                autoSelected: 'selection'
            },
            animations: {
                enabled: false
            },
            id: 'chart1',
            height: 250,
            width: 4000,
            type: 'bar',
            foreColor: '#ccc',
            selection: {
                enabled: false,
                fill: {
                    color: '#fff',
                    opacity: 0.4
                },
                xaxis: {
                    min: new Date('22 Apr 2017 00:00:00').getTime(),
                    max: new Date('22 Apr 2017 00:30:00').getTime()
                }
            }
        },
        colors: ['#FF0080'],
        series: [
            {
                name: 'samples',
                data: data
            }
        ],
        stroke: {
            width: 2
        },
        grid: {
            borderColor: '#444'
        },
        markers: {
            size: 0
        },
        xaxis: {
            type: 'datetime',
            tooltip: {
                enabled: false
            },
            stepSize: 10,
            labels: {
                // formatter: function(value) {
                //     return Math.round((value - basetime) / 60000);
                // }
            }
        },
        yaxis: {
            tickAmount: 2
        }
    };

    chart = new ApexCharts(document.getElementById('chart-bar'), options2);
    chart.render();

    function generateDayWiseTimeSeries(baseval, count, yrange) {
        var i = 0;
        var series = [];
        while (i < count) {
            var x = baseval;
            var y =
                Math.floor(Math.random() * (yrange.max - yrange.min + 1)) + yrange.min;

            series.push([x, y]);
            baseval += 60000;
            i++;
        }
        return series;
    }
});
</script>

<template>
    <div class="grid">
        <div class="col-12">
            <div class="card">
                <div id="chart-bar" style="overflow: auto; height: 280px"></div>
            </div>
        </div>
    </div>
</template>

<style>
#chart {
    max-width: 2500px;
    margin: 35px auto;
}

.apexcharts-tooltip:not(:empty) {
    padding: 6px 9px;
}
</style>
