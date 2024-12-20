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

import Utils from "@/service/Utils";
import Tooltip from "@/service/tooltip/Tooltip";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphData from "@/service/flamegraphs/model/FlamegraphData";
import Frame from "@/service/flamegraphs/model/Frame";
import ContextMenu from "primevue/contextmenu";
import FrameRect from "@/service/flamegraphs/FrameRect";
import VisibleFrame from "@/service/flamegraphs/VisibleFrame";

export default class Flamegraph {

    static HIGHLIGHTED_COLOR = '#ee00ee'

    static FRAME_HEIGHT = 20;

    // set up by draw function

    private canvasHeight: number | null = null;
    private canvasWidth: number | null = null;
    private pxPerSample: number | null = null
    private currentScrollY: number = 0

    private hlFrame: Frame | null = null
    private contextFrame: Frame | null = null

    private currentRoot: Frame

    private currentRootLevel: number = 0
    private currentPattern: RegExp | null = null
    private visibleFrames: VisibleFrame[][]

    private readonly depth: number

    private readonly levels: Frame[][]
    private readonly useWeight: boolean = false
    private readonly contextMenu: ContextMenu
    private readonly hl: HTMLElement

    private readonly flamegraphTooltip: FlamegraphTooltip
    private readonly tooltip: Tooltip
    private readonly canvas: HTMLCanvasElement
    private readonly context: CanvasRenderingContext2D

    constructor(data: FlamegraphData, canvasElementId: string, flamegraphTooltip: FlamegraphTooltip, contextMenu: ContextMenu, useWeight: boolean) {
        this.depth = data.depth;
        this.levels = data.levels;
        this.currentRoot = this.levels[0][0];

        this.contextMenu = contextMenu

        this.canvas = <HTMLCanvasElement>document.getElementById(canvasElementId)!;
        this.canvas.style.height = Math.min(data.depth * Flamegraph.FRAME_HEIGHT, 5000) + "px"
        this.context = this.canvas.getContext('2d')!;

        this.removeAllHighlight();
        this.#createHighlightDiv(this.canvas)
        this.hl = document.getElementById('hl')!;

        this.tooltip = new Tooltip(this.canvas)
        this.flamegraphTooltip = flamegraphTooltip

        this.useWeight = Utils.parseBoolean(useWeight)

        this.visibleFrames = Flamegraph.initializeLevels(this.depth);
        this.resizeCanvas(this.canvas.offsetWidth, this.canvas.offsetHeight);

        this.canvas.addEventListener("contextmenu", (e) => {
            contextMenu.show(e);
            this.contextFrame = this.hlFrame
        });
        this.canvas.onmousemove = this.onMouseMoveEvent();
        this.canvas.onmouseout = this.onMouseOut();
        this.canvas.ondblclick = this.onDoubleClick();
    }

    private onMouseMoveEvent() {
        return (event: MouseEvent) => {
            const level = Math.floor(event.offsetY / Flamegraph.FRAME_HEIGHT);

            if (level >= 0 && level < this.levels.length) {
                let frame = this.lookupFrame(level, event);
                this.hlFrame = frame

                if (frame) {
                    if (frame !== this.currentRoot) {
                        getSelection()!.removeAllRanges();
                    }

                    // if `contextFrame` != null, then context menu is selected.
                    if (this.contextFrame == null) {
                        this.hl.style.left = Math.max(this.leftDistance(frame) - this.leftDistance(this.currentRoot), 0) * this.pxPerSample! + this.canvas.offsetLeft + 'px';
                        this.hl.style.width = Math.min(this.totalValue(frame), this.totalValue(this.currentRoot)) * this.pxPerSample! + 'px';
                        this.hl.style.top = (level * Flamegraph.FRAME_HEIGHT - this.currentScrollY) + this.canvas.offsetTop + 'px';
                        this.hl.firstChild!.textContent = frame.title;
                        this.hl.style.display = 'block';
                    }

                    const tooltipContent =
                        this.flamegraphTooltip.generate(frame, this.levels[0][0].totalSamples, this.levels[0][0].totalWeight)

                    this.tooltip.showTooltip(event, this.currentScrollY, tooltipContent)

                    this.canvas.style.cursor = 'pointer';
                    this.canvas.onclick = () => {
                        if (frame !== this.currentRoot) {
                            this.draw(frame, level, this.currentPattern!);
                        }
                    };
                    return;
                }
            }
        };
    }

    closeContextMenu() {
        this.contextFrame = null
    }

    getContextFrame() {
        return this.contextFrame
    }

    updateScrollPositionY(value: number) {
        this.tooltip.hideTooltip()
        this.closeContextMenu()
        this.contextMenu.hide()

        this.currentScrollY = value
    }

    removeHighlight() {
        // Don't remove highlighting if context menu is active
        if (this.contextFrame == null) {
            this.hl.style.display = 'none';
            this.canvas.title = '';
            this.canvas.style.cursor = '';
            this.canvas.onclick = null;
        }
    }

    removeAllHighlight() {
        let hl = document.getElementById("hl");
        if (hl != null) {
            hl.outerHTML = ""
        }
    }

    private onMouseOut() {
        return () => {
            this.removeHighlight()
            this.tooltip.hideTooltip()
        };
    };

    private onDoubleClick() {
        return () => {
            getSelection()!.selectAllChildren(this.hl);
        };
    };

    #createHighlightDiv(canvas: HTMLCanvasElement) {
        canvas.insertAdjacentHTML(
            'afterend',
            '<div id="hl" style="' +
            ' position: absolute;' +
            ' display: none;' +
            ' overflow: hidden;' +
            ' white-space: nowrap;' +
            ' pointer-events: none;' +
            ' background-color: #ffffe0;' +
            ' font: 12px Arial;' +
            ' height: 20px;' +
            ' padding-top: 3px;"><span style="padding: 0 3px 0 3px"></span></div>'
        )
    }

    private static pct(a: number, b: number) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }

    private lookupFrame(level: number, event: MouseEvent): Frame | null {
        let frames = this.visibleFrames[level];
        for (let i = 0; i < frames.length; i++) {
            let visibleFrame = frames[i];

            if (this.pointInPath(visibleFrame.rect, event.offsetX, event.offsetY)) {
                return visibleFrame.frame;
            }
        }
        return null
    }

    private pointInPath(rect: FrameRect, x: number, y: number) {
        let xPosition = x >= rect.x && x <= (rect.x + rect.width)
        let yPosition = y >= rect.y && y <= (y + rect.height)
        return xPosition && yPosition
    }

    resizeWidthCanvas(width: number) {
        this.resizeCanvas(width, this.canvasHeight!)
    }

    resizeCanvas(width: number, height: number) {
        if (height != null) {
            this.canvasHeight = height;
            this.canvas.height = this.canvasHeight * (devicePixelRatio || 1);
        }

        this.canvasWidth = width;
        this.canvas.style.width = this.canvasWidth + 'px';

        this.canvas.width = this.canvasWidth * (devicePixelRatio || 1);
        if (devicePixelRatio) {
            this.context.scale(devicePixelRatio, devicePixelRatio);
        }
        this.context.font = '12px Arial';
        this.drawRoot();
    }

    static initializeLevels(depth: number) {
        let levels: VisibleFrame[][] = Array(depth + 1);
        for (let h = 0; h < levels.length; h++) {
            levels[h] = [];
        }
        return levels;
    }

    private clearCanvas() {
        this.removeHighlight()
        this.context.fillStyle = '#ffffff';
        this.context.fillRect(0, 0, this.canvasWidth!, this.canvasHeight!);
    }

    drawRoot() {
        this.draw(this.currentRoot, this.currentRootLevel, this.currentPattern!);
    }

    search(pattern: string) {
        this.currentPattern = RegExp(pattern);
        let highlighted = this.draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
        let highlightedTotal = this.calculateHighlighted(highlighted);
        return Flamegraph.pct(highlightedTotal, this.totalValue(this.currentRoot));
    }

    resetSearch() {
        this.currentPattern = null;
        this.draw(this.currentRoot, this.currentRootLevel, this.currentPattern!);
    }

    resetZoom() {
        this.draw(this.levels[0][0], 0, this.currentPattern!);
    }

    private draw(root: Frame, rootLevel: number, pattern: RegExp) {
        this.clearCanvas();
        this.visibleFrames = Flamegraph.initializeLevels(this.depth);

        this.currentRoot = root;
        this.currentRootLevel = rootLevel;

        this.pxPerSample = this.canvasWidth! / this.totalValue(root);

        const xStart = this.leftDistance(root);
        const xEnd = xStart + this.totalValue(root);
        const highlighted = new Map<number, number>()

        for (let level = 0; level < this.levels.length; level++) {
            const y = level * Flamegraph.FRAME_HEIGHT;
            const frames = this.levels[level];

            for (let i = 0; i < frames.length; i++) {
                let frame = frames[i];
                if (this.frame_not_overflow(frame, xStart, xEnd)) {
                    let isHighlighted: boolean = this.isMethodHighlighted(highlighted, frame, pattern);
                    let isUnderRoot = level < rootLevel;

                    const rectangle = this.createRectangle(this.pxPerSample, frame, y, xStart);
                    this.visibleFrames[level].push({rect: rectangle, frame: frame});
                    this.drawFrame(this.pxPerSample, frame, y, xStart, rectangle, isHighlighted, isUnderRoot);
                }
            }
        }

        return highlighted
    }

    private frame_not_overflow(frame: Frame, xStart: number, xEnd: number) {
        return this.leftDistance(frame) < xEnd && this.leftDistance(frame) + this.totalValue(frame) > xStart;
    }

    private isMethodHighlighted(highlighted: Map<number, number>, frame: Frame, pattern: RegExp): boolean {
        const matched = pattern && frame.title.match(pattern) != null
        if (matched) {
            const highlightedValue: number | undefined = highlighted.get(this.leftDistance(frame))
            const alreadyHighlighted: boolean = highlightedValue != undefined && highlightedValue >= this.totalValue(frame)
            if (!alreadyHighlighted) {
                highlighted.set(this.leftDistance(frame), this.totalValue(frame))
            }
            return true
        } else {
            return false
        }
    }

    private calculateHighlighted(highlighted: Map<number, number>) {
        let total = 0;
        let left = 0;
        let obj = Object.fromEntries(highlighted);
        Object.keys(obj)
            .sort(function (a, b) {
                return +a - (+b);
            })
            .forEach(function (x) {
                if (+x >= left) {
                    let valueX: number = obj[x];
                    total += valueX;
                    left = +x + valueX;
                }
            });
        return total;
    }

    private toPath2D(rect: FrameRect) {
        const path = new Path2D()
        path.rect(rect.x, rect.y, rect.width, rect.height)
        return path;
    }

    private createRectangle(pxPerSample: number, frame: Frame, y: number, xStart: number) {
        const x = (this.leftDistance(frame) - xStart) * pxPerSample;
        const width = this.totalValue(frame) * pxPerSample;
        return new FrameRect(x, y, width, Flamegraph.FRAME_HEIGHT);
    }

    private drawFrame(pxPerSample: number, frame: Frame, y: number, xStart: number, rect: FrameRect, isHighlighted: boolean, isUnderRoot: boolean) {
        const path = this.toPath2D(rect)

        this.context.fillStyle = isHighlighted ? Flamegraph.HIGHLIGHTED_COLOR : this.color(frame);
        this.context.strokeStyle = 'white';
        this.context.fill(path);
        this.context.lineWidth = 1;
        this.context.stroke(path);

        // Do we want to fill the text, or the frame is too small and leave it empty
        let totalValue: number = this.totalValue(frame);
        if (totalValue * pxPerSample >= 21) {
            const chars = Math.floor((totalValue * pxPerSample) / 7);
            const title = frame.title.length <= chars ? frame.title : frame.title.substring(0, chars - 2) + '..';
            this.context.fillStyle = '#000000';
            this.context.fillText(title, Math.max(this.leftDistance(frame) - xStart, 0) * pxPerSample + 3, y + 14, totalValue * pxPerSample - 6);
        }

        if (isUnderRoot) {
            this.context.fillStyle = 'rgba(255, 255, 255, 0.5)';
            this.context.fill(path);
        }
    }

    private color(frame: Frame): string {
        if (this.useWeight) {
            return frame.colorWeight
        } else {
            return frame.colorSamples
        }
    }

    private totalValue(frame: Frame): number {
        if (this.useWeight) {
            return frame.totalWeight
        } else {
            return frame.totalSamples
        }
    }

    private leftDistance(frame: Frame): number {
        if (this.useWeight) {
            return frame.leftWeight
        } else {
            return frame.leftSamples
        }
    }
}
