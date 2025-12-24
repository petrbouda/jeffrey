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

import Utils from "@/services/Utils";
import Tooltip from "@/services/tooltip/Tooltip";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphData from "@/services/flamegraphs/model/FlamegraphData";
import Frame from "@/services/flamegraphs/model/Frame";
import FrameRect from "@/services/flamegraphs/FrameRect";
import VisibleFrame from "@/services/flamegraphs/VisibleFrame";
import FrameColorResolver from "@/services/flamegraphs/FrameColorResolver";

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
    private readonly contextMenu: any
    private readonly hl: HTMLElement

    private readonly flamegraphTooltip: FlamegraphTooltip
    private readonly tooltip: Tooltip
    private readonly canvas: HTMLCanvasElement
    private readonly canvasWrapper: HTMLDivElement
    private readonly context: CanvasRenderingContext2D

    // OffscreenCanvas for pre-rendering (smoother zooming)
    private offscreenCanvas: OffscreenCanvas | null = null
    private offscreenContext: OffscreenCanvasRenderingContext2D | null = null
    private preRenderRafId: number | null = null

    // Throttling state for mouse move events
    private mouseMoveRafId: number | null = null
    private pendingMouseEvent: MouseEvent | null = null

    constructor(data: FlamegraphData, canvas: HTMLCanvasElement, flamegraphTooltip: FlamegraphTooltip, contextMenu: any, useWeight: boolean) {
        this.depth = data.depth;
        this.levels = data.levels;
        this.currentRoot = this.levels[0][0];

        this.contextMenu = contextMenu

        this.canvas = canvas;
        this.canvas.style.height = Math.min(data.depth * Flamegraph.FRAME_HEIGHT, 5000) + "px"
        this.context = this.canvas.getContext('2d')!;

        // Create wrapper for canvas and highlight - ensures proper positioning context
        this.canvasWrapper = document.createElement('div');
        this.canvasWrapper.style.position = 'relative';
        this.canvas.parentNode?.insertBefore(this.canvasWrapper, this.canvas);
        this.canvasWrapper.appendChild(this.canvas);

        // Initialize OffscreenCanvas for pre-rendering (if supported)
        this.initOffscreenCanvas();

        this.removeAllHighlight();
        this.#createHighlightDiv(this.canvasWrapper)
        this.hl = document.getElementById('hl')!;

        this.tooltip = new Tooltip(this.canvas)
        this.flamegraphTooltip = flamegraphTooltip

        this.useWeight = Utils.parseBoolean(useWeight)

        this.visibleFrames = Flamegraph.initializeLevels(this.depth);
        this.resizeCanvas(this.canvas.offsetWidth, this.canvas.offsetHeight);

        this.canvas.addEventListener("contextmenu", (e) => {
            // Prevent browser's default context menu
            e.preventDefault();
            
            // Show our custom context menu
            contextMenu.show(e);
            this.contextFrame = this.hlFrame
        });
        this.canvas.onmousemove = this.onMouseMoveEvent();
        this.canvas.onmouseout = this.onMouseOut();
        this.canvas.ondblclick = this.onDoubleClick();
    }

    private onMouseMoveEvent() {
        // Use requestAnimationFrame to throttle mouse move events to ~60fps
        return (event: MouseEvent) => {
            this.pendingMouseEvent = event;

            if (this.mouseMoveRafId === null) {
                this.mouseMoveRafId = requestAnimationFrame(() => {
                    this.mouseMoveRafId = null;
                    if (this.pendingMouseEvent) {
                        this.processMouseMove(this.pendingMouseEvent);
                    }
                });
            }
        };
    }

    private processMouseMove(event: MouseEvent) {
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
                    this.hl.style.left = Math.max(this.leftDistance(frame) - this.leftDistance(this.currentRoot), 0) * this.pxPerSample! + 'px';
                    this.hl.style.width = Math.min(this.totalValue(frame), this.totalValue(this.currentRoot)) * this.pxPerSample! + 'px';
                    this.hl.style.top = (level * Flamegraph.FRAME_HEIGHT) + 'px';
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

                // Schedule pre-rendering of zoomed view for smoother transition
                if (frame !== this.currentRoot) {
                    this.schedulePreRender(frame, level);
                }
                return;
            }
        }
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

    #createHighlightDiv(wrapper: HTMLDivElement) {
        wrapper.insertAdjacentHTML(
            'beforeend',
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

    /**
     * Initialize OffscreenCanvas for pre-rendering
     * Falls back gracefully if not supported
     */
    private initOffscreenCanvas(): void {
        if (typeof OffscreenCanvas !== 'undefined') {
            try {
                this.offscreenCanvas = new OffscreenCanvas(1, 1);
                this.offscreenContext = this.offscreenCanvas.getContext('2d');
            } catch {
                // OffscreenCanvas not supported or failed to initialize
                this.offscreenCanvas = null;
                this.offscreenContext = null;
            }
        }
    }

    /**
     * Resize OffscreenCanvas to match main canvas dimensions
     */
    private resizeOffscreenCanvas(): void {
        if (this.offscreenCanvas && this.canvasWidth && this.canvasHeight) {
            const pixelRatio = devicePixelRatio || 1;
            this.offscreenCanvas.width = this.canvasWidth * pixelRatio;
            this.offscreenCanvas.height = this.canvasHeight * pixelRatio;
            if (this.offscreenContext && pixelRatio !== 1) {
                this.offscreenContext.scale(pixelRatio, pixelRatio);
            }
        }
    }

    /**
     * Pre-render a zoomed view in the background for smoother transitions
     * Called when hovering over a frame to prepare for potential click
     */
    private schedulePreRender(frame: Frame, level: number): void {
        if (!this.offscreenCanvas || !this.offscreenContext) return;

        // Cancel previous pre-render if pending
        if (this.preRenderRafId !== null) {
            cancelAnimationFrame(this.preRenderRafId);
        }

        // Schedule pre-render for next idle frame
        this.preRenderRafId = requestAnimationFrame(() => {
            this.preRenderRafId = null;
            this.preRenderZoomedView(frame, level);
        });
    }

    /**
     * Pre-render the zoomed view on OffscreenCanvas
     */
    private preRenderZoomedView(root: Frame, rootLevel: number): void {
        if (!this.offscreenContext || !this.canvasWidth || !this.canvasHeight) return;

        const ctx = this.offscreenContext;

        // Clear offscreen canvas
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, this.canvasWidth, this.canvasHeight);

        const pxPerSample = this.canvasWidth / this.totalValue(root);
        const xStart = this.leftDistance(root);
        const xEnd = xStart + this.totalValue(root);

        ctx.font = '12px Arial';

        // Render all levels
        for (let level = 0; level < this.levels.length; level++) {
            const y = level * Flamegraph.FRAME_HEIGHT;
            const frames = this.levels[level];

            for (let i = 0; i < frames.length; i++) {
                const frame = frames[i];
                if (this.frame_not_overflow(frame, xStart, xEnd)) {
                    const isUnderRoot = level < rootLevel;
                    this.drawFrameToContext(ctx, pxPerSample, frame, y, xStart, isUnderRoot);
                }
            }
        }
    }

    /**
     * Draw a frame to a specific context (main or offscreen)
     */
    private drawFrameToContext(
        ctx: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D,
        pxPerSample: number,
        frame: Frame,
        y: number,
        xStart: number,
        isUnderRoot: boolean
    ): void {
        const x = (this.leftDistance(frame) - xStart) * pxPerSample;
        const width = this.totalValue(frame) * pxPerSample;

        ctx.fillStyle = this.color(frame);
        ctx.strokeStyle = 'white';
        ctx.fillRect(x, y, width, Flamegraph.FRAME_HEIGHT);
        ctx.lineWidth = 1;
        ctx.strokeRect(x, y, width, Flamegraph.FRAME_HEIGHT);

        // Draw text if frame is wide enough
        const totalValue = this.totalValue(frame);
        if (totalValue * pxPerSample >= 21) {
            const chars = Math.floor((totalValue * pxPerSample) / 7);
            const title = frame.title.length <= chars ? frame.title : frame.title.substring(0, chars - 2) + '..';
            ctx.fillStyle = '#000000';
            ctx.fillText(title, Math.max(this.leftDistance(frame) - xStart, 0) * pxPerSample + 3, y + 14, totalValue * pxPerSample - 6);
        }

        if (isUnderRoot) {
            ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
            ctx.fillRect(x, y, width, Flamegraph.FRAME_HEIGHT);
        }
    }

    private static pct(a: number, b: number) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }

    /**
     * Binary search for frame lookup - O(log n) instead of O(n)
     * Frames are sorted by x position (leftSamples/leftWeight)
     */
    private lookupFrame(level: number, event: MouseEvent): Frame | null {
        const frames = this.visibleFrames[level];
        if (!frames || frames.length === 0) {
            return null;
        }

        const x = event.offsetX;
        let left = 0;
        let right = frames.length - 1;

        while (left <= right) {
            const mid = (left + right) >>> 1;
            const rect = frames[mid].rect;

            if (x < rect.x) {
                right = mid - 1;
            } else if (x > rect.x + rect.width) {
                left = mid + 1;
            } else {
                // Found the frame containing x
                return frames[mid].frame;
            }
        }
        return null;
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

        // Resize OffscreenCanvas for pre-rendering
        this.resizeOffscreenCanvas();

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

    close() {
        // Cancel any pending animation frames to prevent memory leaks
        if (this.mouseMoveRafId !== null) {
            cancelAnimationFrame(this.mouseMoveRafId);
            this.mouseMoveRafId = null;
        }
        if (this.preRenderRafId !== null) {
            cancelAnimationFrame(this.preRenderRafId);
            this.preRenderRafId = null;
        }
        this.pendingMouseEvent = null;

        // Clear canvas event handlers
        this.canvas.onmousemove = null;
        this.canvas.onmouseout = null;
        this.canvas.ondblclick = null;
        this.canvas.onclick = null;

        // Clean up OffscreenCanvas
        this.offscreenCanvas = null;
        this.offscreenContext = null;

        this.removeAllHighlight()
        this.tooltip.hideTooltip()
        this.contextMenu.hide()
    }

    private draw(root: Frame, rootLevel: number, pattern: RegExp) {
        this.clearCanvas();
        this.visibleFrames = Flamegraph.initializeLevels(this.depth);

        this.currentRoot = root;
        this.currentRootLevel = rootLevel;

        this.pxPerSample = this.canvasWidth! / this.totalValue(root);

        const xStart = this.leftDistance(root);
        const xEnd = xStart + this.totalValue(root);
        const highlighted = new Map<number, number>();

        // Render all levels - canvas shows full flamegraph, container handles scrolling
        for (let level = 0; level < this.levels.length; level++) {
            const y = level * Flamegraph.FRAME_HEIGHT;
            const frames = this.levels[level];

            for (let i = 0; i < frames.length; i++) {
                let frame = frames[i];
                if (this.frame_not_overflow(frame, xStart, xEnd)) {
                    let isHighlighted = this.isMethodHighlighted(highlighted, frame, pattern);
                    let isUnderRoot = level < rootLevel;

                    const rectangle = this.createRectangle(this.pxPerSample, frame, y, xStart);
                    this.visibleFrames[level].push({rect: rectangle, frame: frame});
                    this.drawFrame(this.pxPerSample, frame, y, xStart, rectangle, isHighlighted, isUnderRoot);
                }
            }
        }

        return highlighted;
    }

    private isMethodHighlighted(highlighted: Map<number, number>, frame: Frame, pattern: RegExp): boolean {
        const matched = pattern && frame.title.match(pattern) != null;
        if (matched) {
            const highlightedValue = highlighted.get(this.leftDistance(frame));
            const alreadyHighlighted = highlightedValue !== undefined && highlightedValue >= this.totalValue(frame);
            if (!alreadyHighlighted) {
                highlighted.set(this.leftDistance(frame), this.totalValue(frame));
            }
            return true;
        }
        return false;
    }

    private frame_not_overflow(frame: Frame, xStart: number, xEnd: number) {
        return this.leftDistance(frame) < xEnd && this.leftDistance(frame) + this.totalValue(frame) > xStart;
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
        // Differential flamegraph - compute color from diff details
        if (frame.diffDetails) {
            const totalValue = this.useWeight ? frame.totalWeight : frame.totalSamples;
            const diffValue = this.useWeight ? frame.diffDetails.weight : frame.diffDetails.samples;
            // Reconstruct primary and secondary from total (primary + secondary) and diff (primary - secondary)
            const primary = (totalValue + diffValue) / 2;
            const secondary = (totalValue - diffValue) / 2;
            return FrameColorResolver.resolveDiffColor(primary, secondary, frame.type);
        }
        // Regular flamegraph - use frame type color (with beforeMarker for guardian analysis)
        return FrameColorResolver.resolveByType(frame.type, frame.beforeMarker);
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
