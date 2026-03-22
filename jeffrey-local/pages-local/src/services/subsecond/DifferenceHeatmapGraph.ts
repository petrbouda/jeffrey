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
import SubSecondData from "@/services/subsecond/model/SubSecondData";
import DifferenceHeatmapTooltip from "@/services/subsecond/DifferenceHeatmapTooltip";
import SubSecondSerie from "@/services/subsecond/model/SubSecondSerie";
import HeatmapPoint from "@/services/subsecond/model/HeatmapPoint";

export default class DifferenceHeatmapGraph {

    private readonly heatmapElement: HTMLElement;
    private readonly elementQueryId: string;
    private readonly maxValue: number;
    private readonly minValue: number;
    private readonly selectedCallback: any;
    private readonly elementHeatmaps: HTMLElement;
    private readonly heatmap: ApexCharts;
    private readonly scrollerElement: HTMLElement;
    private readonly heatmapTooltip: DifferenceHeatmapTooltip;

    // definition of the heatmap
    private readonly millisInBucket = 20;
    private readonly sizeY = 50;
    private readonly cellWidth = 10;

    private firstSelected: HeatmapPoint | null = null;
    private highlightedAreas: HTMLDivElement[] = [];
    private strokeWidth: number | null = null;

    constructor(
        elementId: string,
        data: SubSecondData,
        minValue: number,
        maxValue: number,
        elementHeatmaps: HTMLElement,
        selectedCallback: any,
        heatmapTooltip: DifferenceHeatmapTooltip
    ) {
        this.heatmapTooltip = heatmapTooltip;
        this.maxValue = maxValue;
        this.minValue = minValue;
        this.selectedCallback = selectedCallback;
        this.elementHeatmaps = elementHeatmaps;
        this.elementQueryId = '#' + elementId;
        this.heatmapElement = document.querySelector(this.elementQueryId)!;
        this.heatmap = new ApexCharts(this.heatmapElement, this.#options(data.series));
        this.scrollerElement = this.heatmapElement.parentElement!;
        this.scrollerElement.onscroll = () => {
            this.#removeHighlightedAreas();
        };
    }

    render() {
        this.heatmap.render();
        this.#setupTooltipPositionAndStyle();

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
            setTimeout(() => {
                this.#setupStrokeWidth();
            }, 200);
        }
    }

    #setupTooltipPositionAndStyle() {
        let el: Element = this.heatmapElement.getElementsByClassName("apexcharts-tooltip apexcharts-theme-light")[0];
        if (el) {
            el.setAttribute("style", "background: transparent; padding: 10px; border: none; box-shadow: none;");
        } else {
            setTimeout(() => {
                let retryEl: Element = this.heatmapElement.getElementsByClassName("apexcharts-tooltip apexcharts-theme-light")[0];
                if (retryEl) {
                    retryEl.setAttribute("style", "background: transparent; padding: 10px; border: none; box-shadow: none;");
                }
            }, 100);
        }
    }

    cleanup() {
        this.#removeHighlightedAreas();
        this.firstSelected = null;
    }

    destroy() {
        this.cleanup();
        this.heatmap.destroy();
    }

    #options(seriesData: SubSecondSerie[]) {
        const numColumns = seriesData.length > 0 && seriesData[0].data.length > 0
            ? seriesData[0].data.length
            : 300;
        const chartWidth = Math.max(numColumns * this.cellWidth, 500);

        // Calculate range thresholds based on data
        const absMax = Math.max(Math.abs(this.minValue), Math.abs(this.maxValue));
        const thresholds = this.#calculateThresholds(absMax);

        return {
            chart: {
                height: 500,
                width: chartWidth,
                type: 'heatmap',
                offsetY: -20,
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
                        this.#onClick(selected);
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
                }
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
            plotOptions: {
                heatmap: {
                    enableShades: false,
                    colorScale: {
                        ranges: [
                            { from: -Infinity, to: -thresholds.veryLarge, color: '#155724', name: 'Very large decrease' },
                            { from: -thresholds.veryLarge, to: -thresholds.large, color: '#28a745', name: 'Large decrease' },
                            { from: -thresholds.large, to: -thresholds.medium, color: '#5cb85c', name: 'Medium decrease' },
                            { from: -thresholds.medium, to: -thresholds.small, color: '#90EE90', name: 'Small decrease' },
                            { from: -thresholds.small, to: thresholds.small, color: '#E6E6E6', name: 'No change' },
                            { from: thresholds.small, to: thresholds.medium, color: '#FFA07A', name: 'Small increase' },
                            { from: thresholds.medium, to: thresholds.large, color: '#FF6347', name: 'Medium increase' },
                            { from: thresholds.large, to: thresholds.veryLarge, color: '#dc3545', name: 'Large increase' },
                            { from: thresholds.veryLarge, to: Infinity, color: '#721c24', name: 'Very large increase' }
                        ]
                    }
                }
            },
            dataLabels: {
                enabled: false
            },
            legend: {
                show: false
            },
            tooltip: {
                custom: (options: any) => {
                    const series = options.series;
                    const seriesIndex = options.seriesIndex;
                    const dataPointIndex = options.dataPointIndex;
                    const w = options.w;

                    const timeBucket = (seriesIndex * 20);

                    if (w.globals.seriesNames[seriesIndex] !== '') {
                        const value = series[seriesIndex][dataPointIndex];
                        const dataPoint = w.config.series[seriesIndex].data[dataPointIndex];
                        const primary = dataPoint?.primary ?? 0;
                        const secondary = dataPoint?.secondary ?? 0;
                        const second = dataPointIndex;
                        const millis = `${timeBucket}-${timeBucket + 20}`;
                        return this.heatmapTooltip.generate(value, primary, secondary, second, millis);
                    } else {
                        return '';
                    }
                }
            },
            series: seriesData
        };
    }

    #calculateThresholds(absMax: number): { small: number; medium: number; large: number; veryLarge: number } {
        // Logarithmic thresholds to make smaller changes more visible
        // Using powers of absMax to distribute colors on a log scale
        if (absMax <= 1) {
            return { small: 0.1, medium: 0.3, large: 0.5, veryLarge: 0.8 };
        }

        // Log-based thresholds: small changes get more color differentiation
        // Thresholds at approximately 5%, 15%, 35%, 65% on logarithmic scale
        const small = Math.max(1, Math.pow(absMax, 0.25));
        const medium = Math.max(2, Math.pow(absMax, 0.45));
        const large = Math.max(5, Math.pow(absMax, 0.65));
        const veryLarge = Math.max(10, Math.pow(absMax, 0.85));

        return {
            small: Math.round(small),
            medium: Math.round(medium),
            large: Math.round(large),
            veryLarge: Math.round(veryLarge)
        };
    }

    #onClick(selected: HeatmapPoint) {
        if (selected.dataPointIndex === -1 && selected.seriesIndex === -1) {
            return;
        }

        if (this.firstSelected == null && this.highlightedAreas.length != 0) {
            this.#removeHighlightedAreas();
            this.#removeCellSelection(selected.seriesIndex, selected.dataPointIndex);
            return;
        }

        if (this.firstSelected == null) {
            // Explicitly copy primitive values to avoid any reference issues
            this.firstSelected = new HeatmapPoint(selected.seriesIndex, selected.dataPointIndex);
            this.#removeHighlightedAreas();
        } else {
            this.highlightedAreas = this.#calculateHighlightedArea(
                this.firstSelected.dataPointIndex,
                this.firstSelected.seriesIndex,
                selected.dataPointIndex,
                selected.seriesIndex
            );

            this.highlightedAreas.forEach(function (el: HTMLDivElement) {
                document.body.appendChild(el);
            });

            const startEndTime = this.#calculateStartEnd(
                this.firstSelected.dataPointIndex, this.firstSelected.seriesIndex,
                selected.dataPointIndex, selected.seriesIndex
            );

            this.selectedCallback(startEndTime[0], startEndTime[1]);

            this.#removeCellSelection(selected.seriesIndex, selected.dataPointIndex);
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
        const strokeWidth = this.strokeWidth!!;

        const rect = document.querySelector(this.elementQueryId + ' rect[i="' + (this.sizeY - 1) + '"][j="0"]')!!
            .getBoundingClientRect();

        const scrollLeft = this.scrollerElement.scrollLeft;

        if (x1 == x2) {
            const rectHeight = (y2 - y1 + 1) * rect.height;
            const rectWidth = rect.width;
            const rectTop = (this.sizeY - y2 - 1) * rect.height + rect.top + window.scrollY;
            const rectLeft = rect.width * x1 + rect.left + this.elementHeatmaps.scrollLeft;
            return [this.#createHighlightElement(rectLeft - scrollLeft, rectTop + strokeWidth, rectHeight, rectWidth)];
        }

        const rects = [];

        const fRectHeight = (this.sizeY - y1) * rect.height;
        const fRectWidth = rect.width;
        const fRectTop = rect.top + +window.scrollY;
        const fRectLeft = rect.width * x1 + rect.left + this.elementHeatmaps.scrollLeft;
        rects.push(this.#createHighlightElement(fRectLeft - scrollLeft, fRectTop + strokeWidth, fRectHeight, fRectWidth));

        const lRectHeight = (y2 + 1) * rect.height;
        const lRectWidth = rect.width;
        const lRectTop = (this.sizeY - y2 - 1) * rect.height + rect.top + +window.scrollY;
        const lRectLeft = rect.width * x2 + rect.left + this.elementHeatmaps.scrollLeft;
        rects.push(this.#createHighlightElement(lRectLeft - scrollLeft, lRectTop + strokeWidth, lRectHeight, lRectWidth));

        if ((x2 - x1) > 1) {
            const mRectHeight = this.sizeY * rect.height;
            const mRectWidth = rect.width * (x2 - x1 - 1);
            const mRectTop = rect.top + +window.scrollY;
            const mRectLeft = (rect.width * (x1 + 1)) + rect.left + this.elementHeatmaps.scrollLeft;
            rects.push(this.#createHighlightElement(mRectLeft - scrollLeft, mRectTop + strokeWidth, mRectHeight, mRectWidth));
        }
        return rects;
    }

    #createHighlightElement(x: number, y: number, height: number, width: number): HTMLDivElement {
        const area: HTMLDivElement = document.createElement("div") as HTMLDivElement;
        area.setAttribute(
            "style", "width: " + width + "px;" + " height: " + height + "px; "
            + "position: absolute; overflow: hidden; background-color:rgba(0,0,0,0.2); "
            + "top: " + y + "px; left:" + x + "px"
        );

        area.addEventListener('click', () => this.#removeHighlightedAreas());
        return area;
    }

    #calculateStartEnd(x1: number, y1: number, x2: number, y2: number) {
        // Ensure values are numbers (ApexCharts may return strings)
        const col1 = Number(x1);
        const row1 = Number(y1);
        const col2 = Number(x2);
        const row2 = Number(y2);

        // Ensure start is before end: compare by column first, then by row within same column
        // Note: row is seriesIndex (bucket), where LOWER index = EARLIER time within a second
        const firstIsBefore = col1 < col2 || (col1 === col2 && row1 <= row2);
        if (firstIsBefore) {
            return [this.#calculateStartTime(col1, row1), this.#calculateEndTime(col2, row2)];
        } else {
            return [this.#calculateStartTime(col2, row2), this.#calculateEndTime(col1, row1)];
        }
    }

    #calculateStartTime(second: number, millis: number) {
        // Ensure values are numbers (ApexCharts may return strings)
        const sec = Number(second);
        const ms = Number(millis);
        return [sec, ms * this.millisInBucket];
    }

    #calculateEndTime(second: number, millis: number) {
        // Ensure values are numbers (ApexCharts may return strings)
        const sec = Number(second);
        const ms = Number(millis);
        // Use exclusive upper bound (-1) to match heatmap bucket boundaries.
        // Bucket N contains events where: N*20 <= millisInSecond < (N+1)*20
        // So end time should be (N+1)*20 - 1 to exclude events in the next bucket.
        let endTimeMillis = (ms * this.millisInBucket) + this.millisInBucket - 1;
        if (endTimeMillis >= 1000) {
            return [sec + 1, 0];
        } else {
            return [sec, endTimeMillis];
        }
    }

    #removeCellSelection(row: number, column: number) {
        document.querySelector(this.elementQueryId + ' rect[i="' + row + '"][j="' + column + '"]')!!
            .removeAttribute("selected");
    }
}
