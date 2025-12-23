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

import ApexCharts from "apexcharts"
import SubSecondData from "@/services/subsecond/model/SubSecondData.ts";
import HeatmapTooltip from "@/services/subsecond/HeatmapTooltip.ts";
import SubSecondSerie from "@/services/subsecond/model/SubSecondSerie.ts";
import HeatmapPoint from "@/services/subsecond/model/HeatmapPoint.ts";

export default class HeatmapGraph {

    private readonly heatmapElement: HTMLElement;
    private readonly elementQueryId: string;
    private readonly maxValue: number
    private readonly selectedCallback: any;
    private readonly elementHeatmaps: HTMLElement
    private readonly heatmap: ApexCharts;
    private readonly scrollerElement: HTMLElement
    private readonly heatmapTooltip: HeatmapTooltip;

    // definition of the heatmap
    private readonly millisInBucket = 20
    private readonly sizeY = 50;
    private readonly cellWidth = 10; // pixels per column (second)

    private firstSelected: HeatmapPoint | null = null;

    private highlightedAreas : HTMLDivElement[] = [];
    private strokeWidth: number | null = null;

    constructor(
        elementId: string,
        data: SubSecondData,
        elementHeatmaps: HTMLElement,
        selectedCallback: any,
        heatmapTooltip: HeatmapTooltip) {

        this.heatmapTooltip = heatmapTooltip;
        this.maxValue = data.maxvalue
        this.selectedCallback = selectedCallback;
        this.elementHeatmaps = elementHeatmaps
        this.elementQueryId = '#' + elementId
        this.heatmapElement = document.querySelector(this.elementQueryId)!
        this.heatmap = new ApexCharts(this.heatmapElement, this.#options(data.series));
        this.scrollerElement = this.heatmapElement.parentElement!
        this.scrollerElement.onscroll = () => {
            this.#removeHighlightedAreas()
        }
    }

    render() {
        this.heatmap.render();
        this.#setupTooltipPositionAndStyle()

        // Wait for the heatmap to be fully rendered before accessing rect elements
        setTimeout(() => {
            this.#setupStrokeWidth();
        }, 150);
    }

    #setupStrokeWidth() {
        const rect: Element | null = document.querySelector(this.elementQueryId + ' rect[i="' + (this.sizeY - 1) + '"][j="0"]');
        if (rect) {
            const strokeWidthAttr = rect.getAttribute("stroke-width");
            if (strokeWidthAttr) {
                this.strokeWidth = parseInt(strokeWidthAttr) / 2;
            }
        } else {
            // Retry after a longer delay if the rect element is not ready yet
            setTimeout(() => {
                this.#setupStrokeWidth();
            }, 200);
        }
    }

    #setupTooltipPositionAndStyle() {
        let el: Element = this.heatmapElement.getElementsByClassName("apexcharts-tooltip apexcharts-theme-light")[0]
        if (el) {
            el.setAttribute("style", "background: transparent; padding: 10px; border: none; box-shadow: none;");
        } else {
            // Retry after a short delay if the tooltip element is not ready yet
            setTimeout(() => {
                let retryEl: Element = this.heatmapElement.getElementsByClassName("apexcharts-tooltip apexcharts-theme-light")[0]
                if (retryEl) {
                    retryEl.setAttribute("style", "background: transparent; padding: 10px; border: none; box-shadow: none;");
                }
            }, 100);
        }
    }

    cleanup() {
        this.#removeHighlightedAreas()
        this.firstSelected = null
    }

    destroy() {
        this.cleanup()
        this.heatmap.destroy()
    }

    #options(seriesData: SubSecondSerie[]) {
        // Calculate width based on number of columns (seconds)
        const numColumns = seriesData.length > 0 && seriesData[0].data.length > 0
            ? seriesData[0].data.length
            : 300; // default to 300 seconds (5 minutes)
        const chartWidth = Math.max(numColumns * this.cellWidth, 500); // minimum 500px

        return {
            chart: {
                height: 500,
                width: chartWidth,
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
                    click: (_event: MouseEvent, _chartContext: any, selected: HeatmapPoint) => {
                        this.#onClick(selected)
                    }
                }
            },
            yaxis: {
                labels: {
                    formatter: function (value: number) {
                        if (value % 100 === 0) {
                            return value;
                        }
                    }
                },
                min: 0,
                max: this.maxValue
            },
            xaxis: {
                tickPlacement: 'on',
                labels: {
                    formatter: function (value: number) {
                        if (value % 5 === 0) {
                            return String(value);
                        }
                        return '';
                    }
                }
            },
            colors: ['#0022ff'],
            plotOptions: {
                heatmap: {
                    shadeIntensity: 1
                }
            },
            dataLabels: {
                enabled: false
            },
            tooltip: {
                custom: (options: any) => {
                    const series = options.series
                    const seriesIndex = options.seriesIndex
                    const dataPointIndex = options.dataPointIndex
                    const w = options.w

                    const timeBucket = (seriesIndex * 20)

                    if (w.globals.seriesNames[seriesIndex] !== '') {
                        const value = series[seriesIndex][dataPointIndex]
                        const second = dataPointIndex
                        const millis = `${timeBucket}-${timeBucket + 20}`;
                        return this.heatmapTooltip.generate(value, second, millis)
                    } else {
                        return '';
                    }
                }
            },
            series: seriesData
        };
    }

    #onClick(selected: HeatmapPoint) {
        if (selected.dataPointIndex === -1 && selected.seriesIndex === -1) {
            return;
        }

        // Area is selected, and we want to remove it using the next click
        if (this.firstSelected == null && this.highlightedAreas.length != 0) {
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
            this.highlightedAreas.forEach(function (el: HTMLDivElement) {
                document.body.appendChild(el);
            })

            const startEndTime = this.#calculateStartEnd(
                this.firstSelected.dataPointIndex, this.firstSelected.seriesIndex,
                selected.dataPointIndex, selected.seriesIndex)

            this.selectedCallback(startEndTime[0], startEndTime[1])

            this.#removeCellSelection(selected.seriesIndex, selected.dataPointIndex)
            this.firstSelected = null;
        }
    }

    #removeHighlightedAreas() {
        if (this.highlightedAreas.length != 0) {
            this.highlightedAreas.forEach(function (el) {
                el.remove();
            });
        }
        this.highlightedAreas = [];
    }

    #calculateHighlightedArea(x1: number, y1: number, x2: number, y2: number): HTMLDivElement[] {
        if (x1 > x2 || (x1 === x2 && y1 > y2)) {
            return this.#_calculateHighlightedArea(x2, y2, x1, y1);
        } else {
            return this.#_calculateHighlightedArea(x1, y1, x2, y2);
        }
    }

    #_calculateHighlightedArea(x1: number, y1: number, x2: number, y2: number): HTMLDivElement[] {
        const strokeWidth = this.strokeWidth!!

        const rect = document.querySelector(this.elementQueryId + ' rect[i="' + (this.sizeY - 1) + '"][j="0"]')!!
            .getBoundingClientRect()

        const scrollLeft = this.scrollerElement.scrollLeft

        // single column selection
        if (x1 == x2) {
            const rectHeight = (y2 - y1 + 1) * rect.height;
            const rectWidth = rect.width;
            const rectTop = (this.sizeY - y2 - 1) * rect.height + rect.top + window.scrollY;
            const rectLeft = rect.width * x1 + rect.left + this.elementHeatmaps.scrollLeft;
            return [this.#createHighlightElement(rectLeft - scrollLeft, rectTop + strokeWidth, rectHeight, rectWidth)];
        }

        const rects = [];

        // the first column
        const fRectHeight = (this.sizeY - y1) * rect.height;
        const fRectWidth = rect.width;
        const fRectTop = rect.top + +window.scrollY;
        const fRectLeft = rect.width * x1 + rect.left + this.elementHeatmaps.scrollLeft;
        rects.push(this.#createHighlightElement(fRectLeft - scrollLeft, fRectTop + strokeWidth, fRectHeight, fRectWidth))

        // the last column
        const lRectHeight = (y2 + 1) * rect.height;
        const lRectWidth = rect.width;
        const lRectTop = (this.sizeY - y2 - 1) * rect.height + rect.top + +window.scrollY;
        const lRectLeft = rect.width * x2 + rect.left + this.elementHeatmaps.scrollLeft;
        rects.push(this.#createHighlightElement(lRectLeft - scrollLeft, lRectTop + strokeWidth, lRectHeight, lRectWidth))

        // rectangle between the first and the last columns
        if ((x2 - x1) > 1) {
            const mRectHeight = this.sizeY * rect.height;
            const mRectWidth = rect.width * (x2 - x1 - 1);
            const mRectTop = rect.top + +window.scrollY;
            const mRectLeft = (rect.width * (x1 + 1)) + rect.left + this.elementHeatmaps.scrollLeft;
            rects.push(this.#createHighlightElement(mRectLeft - scrollLeft, mRectTop + strokeWidth, mRectHeight, mRectWidth))
        }
        return rects;
    }

    #createHighlightElement(x: number, y: number, height: number, width: number): HTMLDivElement {
        const area: HTMLDivElement = document.createElement("div") as HTMLDivElement
        area.setAttribute(
            "style", "width: " + width + "px;" + " height: " + height + "px; "
            + "position: absolute; overflow: hidden; background-color:rgba(0,0,0,0.2); "
            + "top: " + y + "px; left:" + x + "px");

        area.addEventListener('click', () => this.#removeHighlightedAreas());
        return area
    }

    #calculateStartEnd(x1: number, y1: number, x2: number, y2: number) {
        if (x1 > x2 || (x1 === x2 && y1 > y2)) {
            return [this.#calculateStartTime(x2, y2), this.#calculateEndTime(x1, y1)]
        } else {
            return [this.#calculateStartTime(x1, y1), this.#calculateEndTime(x2, y2)]
        }
    }

    #calculateStartTime(second: number, millis: number) {
        return [second, millis * this.millisInBucket];
    }

    #calculateEndTime(second: number, millis: number) {
        let endTimeMillis = (millis * this.millisInBucket) + this.millisInBucket;
        if (endTimeMillis >= 1000) {
            return [second + 1, 0];
        } else {
            return [second, endTimeMillis];
        }
    }

    #removeCellSelection(row: number, column: number) {
        document.querySelector(this.elementQueryId + ' rect[i="' + row + '"][j="' + column + '"]')!!
            .removeAttribute("selected")
    }
}
