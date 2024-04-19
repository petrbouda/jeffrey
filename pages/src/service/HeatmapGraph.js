export default class HeatmapGraph {

    firstSelected = null;
    sizeX = null;
    sizeY = 50;
    matrix = null;
    _rect = null;
    highlightedAreas = null;
    strokeWidth = null;
    scrollerElement = null
    maxValue = null
    millisInBucket = 20
    elementQueryId = null
    elementHeatmaps = null

    constructor(elementId, data, elementHeatmaps, selectedFn) {
        this.sizeX = data.series[0].data.length;
        this.maxValue = data.maxvalue
        this.data = data
        this.selectedFn = selectedFn;
        this.elementId = elementId
        this.elementHeatmaps = elementHeatmaps
        this.elementQueryId = '#' + this.elementId

        let heatmapElement = document.querySelector(this.elementQueryId);
        this.heatmap = new ApexCharts(heatmapElement, this.#options(data.series));

        this.scrollerElement = heatmapElement.parentElement
        this.scrollerElement.onscroll = () => {
            this.#removeHighlightedAreas()
        }
    }

    render() {
        this.heatmap.render();
        this.matrix = document.querySelector(this.elementQueryId + ' g[class=\'apexcharts-heatmap\']').children;

        const rect = document.querySelector(this.elementQueryId + ' rect[i="' + (this.sizeY - 1) + '"][j="0"]')
        this.strokeWidth = rect.getAttribute("stroke-width") / 2;
    }

    cleanup() {
        this.#removeHighlightedAreas()
        this.firstSelected = null
    }

    destroy() {
        this.cleanup()
        this.heatmap.destroy()
    }

    #options(seriesData) {
        return {
            chart: {
                height: 500,
                width: 3000,
                type: 'heatmap',
                selection: {
                    enabled: false
                },
                animations: {
                    enabled: false
                },
                toolbar: {
                    show: false
                },
                events: {
                    click: (event, chartContext, selected) => {
                        this.#onClick(event, chartContext, selected)
                    }
                }
            },
            yaxis: {
                labels: {
                    formatter: function (value) {
                        if (value % 100 === 0) {
                            return value;
                        }
                    }
                },
                min: 0,
                max: this.maxValue
            },
            xaxis: {
                labels: {
                    formatter: function (value) {
                        if (value % 5 === 0) {
                            return value;
                        }
                    }
                }
            },
            colors: ['#5A9BD5'],
            plotOptions: {
                heatmap: {
                    shadeIntensity: 1
                }
            },
            dataLabels: {
                enabled: false
            },
            tooltip: {
                custom: function ({series, seriesIndex, dataPointIndex, w}) {
                    const timeBucket = (seriesIndex * 20)

                    if (w.globals.seriesNames[seriesIndex] !== '') {
                        return HeatmapGraph.#tooltipTable(
                            series[seriesIndex][dataPointIndex], dataPointIndex, `${timeBucket}-${timeBucket + 20}`)
                    } else {
                        return '';
                    }
                }
            },
            series: seriesData
        };
    }

    static #tooltipTable(samples, second, millis) {
        return `
            <table>
                <tr>
                    <th style="text-align: right">Samples:</th>
                    <td>${samples}<td>
                </tr>
                <tr>
                    <th style="text-align: right">Second:</th>
                    <td>${second}<td>
                </tr>
                <tr>
                    <th style="text-align: right">Millis:</th>
                    <td>${millis}<td>
                </tr>
            </table>`
    }

    #onClick(event, chartContext, selected) {
        if (selected.dataPointIndex === -1 && selected.seriesIndex === -1) {
            return;
        }

        // Area is selected, and we want to remove it using the next click
        if (this.firstSelected == null && this.highlightedAreas != null) {
            this.#removeHighlightedAreas()
            this.#removeCellSelection(selected.seriesIndex, selected.dataPointIndex)
            return;
        }

        if (this.firstSelected == null) {
            this.firstSelected = selected;
            this.#removeHighlightedAreas()
        } else {
            this.highlightedAreas = this.#calculateHighlightedArea(
                this.firstSelected.dataPointIndex,
                this.firstSelected.seriesIndex,
                selected.dataPointIndex,
                selected.seriesIndex);

            // visualize highlighted areas
            this.highlightedAreas.forEach(function (el) {
                document.body.appendChild(el)
            })

            const startEndTime = this.#calculateStartEnd(
                this.firstSelected.dataPointIndex, this.firstSelected.seriesIndex,
                selected.dataPointIndex, selected.seriesIndex)

            this.selectedFn(this.elementId, event, startEndTime[0], startEndTime[1])

            this.#removeCellSelection(selected.seriesIndex, selected.dataPointIndex)
            this.firstSelected = null;
        }
    }

    #removeHighlightedAreas() {
        if (this.highlightedAreas != null) {
            this.highlightedAreas.forEach(function (el) {
                el.remove();
            });
        }
        this.highlightedAreas = null;
    }

    #calculateHighlightedArea(x1, y1, x2, y2) {
        if (x1 > x2 || (x1 === x2 && y1 > y2)) {
            return this.#_calculateHighlightedArea(x2, y2, x1, y1);
        } else {
            return this.#_calculateHighlightedArea(x1, y1, x2, y2);
        }
    }

    #_calculateHighlightedArea(x1, y1, x2, y2) {
        const heatmapElement = document.querySelector(this.elementQueryId);
        const scrollerElement = heatmapElement.parentElement

        const rect = document.querySelector(this.elementQueryId + ' rect[i="' + (this.sizeY - 1) + '"][j="0"]')
            .getBoundingClientRect()

        // single column selection
        if (x1 == x2) {
            const rectHeight = (y2 - y1 + 1) * rect.height;
            const rectWidth = rect.width;
            const rectTop = (this.sizeY - y2 - 1) * rect.height + rect.top + window.scrollY;
            const rectLeft = rect.width * x1 + rect.left + this.elementHeatmaps.scrollLeft;
            return [this.#createHighlightElement(rectLeft - scrollerElement.scrollLeft, rectTop + this.strokeWidth, rectHeight, rectWidth)];
        }

        const rects = [];

        // the first column
        const fRectHeight = (this.sizeY - y1) * rect.height;
        const fRectWidth = rect.width;
        const fRectTop = rect.top +  + window.scrollY;
        const fRectLeft = rect.width * x1 + rect.left + this.elementHeatmaps.scrollLeft;
        rects.push(this.#createHighlightElement(fRectLeft - scrollerElement.scrollLeft, fRectTop + this.strokeWidth, fRectHeight, fRectWidth))

        // the last column
        const lRectHeight = (y2 + 1) * rect.height;
        const lRectWidth = rect.width;
        const lRectTop = (this.sizeY - y2 - 1) * rect.height + rect.top +  + window.scrollY;
        const lRectLeft = rect.width * x2 + rect.left + this.elementHeatmaps.scrollLeft;
        rects.push(this.#createHighlightElement(lRectLeft - scrollerElement.scrollLeft, lRectTop + this.strokeWidth, lRectHeight, lRectWidth))

        // rectangle between the first and the last columns
        if ((x2 - x1) > 1) {
            const mRectHeight = this.sizeY * rect.height;
            const mRectWidth = rect.width * (x2 - x1 - 1);
            const mRectTop = rect.top +  + window.scrollY;
            const mRectLeft = (rect.width * (x1 + 1)) + rect.left + this.elementHeatmaps.scrollLeft;
            rects.push(this.#createHighlightElement(mRectLeft - scrollerElement.scrollLeft, mRectTop + this.strokeWidth, mRectHeight, mRectWidth))
        }
        return rects;
    }

    #createHighlightElement(x, y, height, width) {
        const area = document.createElement("div");
        area.setAttribute(
            "style", "width: " + width + "px;" + " height: " + height + "px; "
            + "position: absolute; overflow: hidden; background-color:rgba(0,0,0,0.2); "
            + "top: " + y + "px; left:" + x + "px");

        area.addEventListener('click', (event) => {
            this.#removeHighlightedAreas()
        });
        return area
    }

    #calculateStartEnd(x1, y1, x2, y2) {
        if (x1 > x2 || (x1 === x2 && y1 > y2)) {
            return [this.#calculateStartTime(x2, y2), this.#calculateEndTime(x1, y1)]
        } else {
            return [this.#calculateStartTime(x1, y1), this.#calculateEndTime(x2, y2)]
        }
    }

    #calculateStartTime(second, millis) {
        return [second, millis * this.millisInBucket];
    }

    #calculateEndTime(second, millis) {
        let endTimeMillis = (millis * this.millisInBucket) + this.millisInBucket;
        if (endTimeMillis >= 1000) {
            return [second + 1, 0];
        } else {
            return [second, endTimeMillis];
        }
    }

    #removeCellSelection(row, column) {
        document.querySelector(this.elementQueryId + ' rect[i="' + row + '"][j="' + column + '"]')
            .removeAttribute("selected")
    }
}
