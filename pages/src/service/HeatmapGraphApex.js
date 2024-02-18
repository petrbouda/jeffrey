export default class HeatmapGraphApex {

    firstSelected = null;
    sizeX = null;
    sizeY= 50;
    matrix = null;
    _rect = null;
    highlightedAreas = null;
    strokeWidth = null;
    scrollerElement = null


    constructor(elementId, data) {
        this.sizeX = data[0].data.size;
        let heatmapElement = document.querySelector('#' + elementId);
        this.heatmap = new ApexCharts(heatmapElement, this.#options(data));
        this.scrollerElement = heatmapElement.parentElement
        this.scrollerElement.onscroll = () => {
            this.#removeHighlightedAreas()
        }
    }

    #options(seriesData) {
        return {
            chart: {
                height: 500,
                width: 4000,
                type: 'heatmap',
                animations: {
                    enabled: false
                },
                events: {
                    click: (event, chartContext, selected) => {
                        if (selected.dataPointIndex === -1 && selected.seriesIndex === -1) {
                            return;
                        }

                        if (this.firstSelected == null) {
                            this.firstSelected = selected;
                            this.#removeHighlightedAreas()
                            this.highlightedAreas = null;
                        } else {
                            this.highlightedAreas = this.#calculateHighlightedArea(
                                this.firstSelected.dataPointIndex,
                                this.firstSelected.seriesIndex,
                                selected.dataPointIndex,
                                selected.seriesIndex);

                            // visualize highlighted areas
                            this.highlightedAreas.forEach(function(el) {
                                document.body.appendChild(el)
                            })

                            this.firstSelected = null;
                        }
                    }
                }
            },
            yaxis: {
                labels: {
                    formatter: function(value) {
                        if (value % 100 === 0) {
                            return value;
                        }
                    }
                }
            },
            xaxis: {
                labels: {
                    formatter: function(value) {
                        if (value % 5 === 0) {
                            return value;
                        }
                    }
                },
                group: {
                    style: {
                        fontSize: '10px',
                        fontWeight: 700
                    },
                    groups: [
                        { title: '1. minute', cols: 60 },
                        { title: '2. minute', cols: 60 },
                        { title: '3. minute', cols: 60 },
                        { title: '4. minute', cols: 60 },
                        { title: '5. minute', cols: 60 }
                    ]
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
                custom: function({ series, seriesIndex, dataPointIndex, w }) {
                    if (w.globals.seriesNames[seriesIndex] !== '') {
                        return series[seriesIndex][dataPointIndex];
                    } else {
                        return '';
                    }
                }
            },
            series: seriesData
        };
    }

    #removeHighlightedAreas() {
        if (this.highlightedAreas != null) {
            this.highlightedAreas.forEach(function(el) {
                el.remove();
            });
        }
    }

    #calculateHighlightedArea(x1, y1, x2, y2) {
        if (x1 > x2 || (x1 === x2 && y1 > y2)) {
            return this.#_calculateHighlightedArea(x2, y2, x1, y1);
        } else {
            return this.#_calculateHighlightedArea(x1, y1, x2, y2);
        }
    }

    #_calculateHighlightedArea(x1, y1, x2, y2) {
        // single column selection
        if (x1 == x2) {
            const rectHeight = (y2 - y1 + 1) * this._rect.height;
            const rectWidth = this._rect.width;
            const rectTop = (this.sizeY - y2 - 1) * this._rect.height + this._rect.y;
            const rectLeft = this._rect.width * x1 + this._rect.x;
            return [this.#createHighlightElement(rectLeft - this.scrollerElement.scrollLeft, rectTop + this.strokeWidth, rectHeight, rectWidth)];
        }

        const rects = [];

        // the first column
        const fRectHeight = (this.sizeY - y1) * this._rect.height;
        const fRectWidth = this._rect.width;
        const fRectTop = this._rect.y;
        const fRectLeft = this._rect.width * x1 + this._rect.x;
        rects.push(this.#createHighlightElement(fRectLeft - this.scrollerElement.scrollLeft, fRectTop + this.strokeWidth, fRectHeight, fRectWidth))

        // the last column
        const lRectHeight = (y2 + 1) * this._rect.height;
        const lRectWidth = this._rect.width;
        const lRectTop = (this.sizeY - y2 - 1) * this._rect.height + this._rect.y;
        const lRectLeft = this._rect.width * x2 + this._rect.x;
        rects.push(this.#createHighlightElement(lRectLeft - this.scrollerElement.scrollLeft, lRectTop + this.strokeWidth, lRectHeight, lRectWidth))

        // rectangle between the first and the last columns
        if ((x2 - x1) > 1) {
            const mRectHeight = this.sizeY * this._rect.height;
            const mRectWidth = this._rect.width * (x2 - x1 - 1);
            const mRectTop = this._rect.y;
            const mRectLeft = (this._rect.width * (x1 + 1)) + this._rect.x;
            rects.push(this.#createHighlightElement(mRectLeft - this.scrollerElement.scrollLeft, mRectTop + this.strokeWidth, mRectHeight, mRectWidth))
        }
        return rects;
    }

    #createHighlightElement(x, y, height, width) {
        const newDiv = document.createElement("div");
        newDiv.setAttribute(
            "style", "width: " + width + "px;" + " height: " + height + "px; "
            + "position: absolute; overflow: hidden; background-color:rgba(0,0,0,0.2); "
            + "top: " + y + "px; left:" + x + "px");
        return newDiv
    }

    render() {
        this.heatmap.render();
        this.matrix = document.querySelector('g[class=\'apexcharts-heatmap\']').children;

        const rect = document.querySelector('rect[i="' + (this.sizeY - 1) + '"][j="0"]')
        this._rect = rect.getBoundingClientRect()
        this.strokeWidth = rect.getAttribute("stroke-width") / 2;
    }


    // #selectAllPoints(x1, y1, x2, y2) {
    //     if (x1 > x2 || (x1 === x2 && y1 > y2)) {
    //         return this.#_selectAllPoints(x2, y2, x1, y1);
    //     } else {
    //         return this.#_selectAllPoints(x1, y1, x2, y2);
    //     }
    // }
    //
    // #_selectAllPoints(x1, y1, x2, y2) {
    //     const nodes = [];
    //
    //     let rowSize = this.matrix.length;
    //     for (let i in this.matrix) {
    //         let columns = this.matrix[i].children;
    //         let reversedI = rowSize - i - 1;
    //
    //         for (let j in columns) {
    //             // columns before the selected area
    //             if (j < x1) {
    //                 continue;
    //             }
    //
    //             // end of row processing, go to the next row
    //             if (j > x2) {
    //                 break;
    //             }
    //
    //             // the area between the selected points
    //             if (j > x1 && j < x2) {
    //                 nodes.push(columns[j]);
    //                 continue;
    //             }
    //
    //             // both point were selected in the same column
    //             if (j == x1 && j == x2) {
    //                 if (reversedI >= y1 && reversedI <= y2) {
    //                     nodes.push(columns[j]);
    //                     break;
    //                 }
    //             } else {
    //                 // handle the first and the last column
    //                 if (j == x1 && reversedI >= y1) {
    //                     nodes.push(columns[j]);
    //                 } else if (j == x2 && reversedI <= y2) {
    //                     nodes.push(columns[j]);
    //                 }
    //             }
    //         }
    //     }
    //     return nodes;
    // }
}
