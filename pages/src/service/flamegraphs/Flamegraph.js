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

import FlamegraphTooltips from "@/service/flamegraphs/FlamegraphTooltips";
import GraphType from "@/service/flamegraphs/GraphType";
import Utils from "@/service/Utils";
import Tooltip from "@/service/Tooltip";

export default class Flamegraph {
    static HIGHLIGHTED_COLOR = '#ee00ee'

    static FRAME_HEIGHT = 20;

    depth = null;

    // set up by draw function
    canvasHeight = null;
    canvasWidth = null;
    currentRoot = null;
    currentRootLevel = null;
    pxPerSample = null

    reversed = true;

    visibleFrames = [];
    currentScrollY = 0

    hlFrame = null

    contextMenu = null
    contextFrame = null
    eventType = null
    tooltipType = FlamegraphTooltips.BASIC
    useWeight = false

    constructor(data, canvasElementId, contextMenu, eventType, useWeight, graphType) {
        this.depth = data.depth;
        this.levels = data.levels;
        this.currentRoot = this.levels[0][0];
        this.currentRootLevel = 0;
        this.currentPattern = null;

        this.contextMenu = contextMenu
        this.eventType = eventType

        const isDifferential = graphType === GraphType.DIFFERENTIAL
        this.tooltipType = FlamegraphTooltips.resolveType(eventType, isDifferential)
        this.canvas = document.getElementById(canvasElementId);
        this.canvas.style.height = Math.min(data.depth * Flamegraph.FRAME_HEIGHT, 5000) + "px"
        this.context = this.canvas.getContext('2d');
        this.removeAllHighlight();
        this.#createHighlightDiv(this.canvas)
        this.hl = document.getElementById('hl');

        this.flamegraphTooltip = new Tooltip(this.canvas)

        this.useWeight = Utils.parseBoolean(useWeight)

        this.visibleFrames = Flamegraph.initializeLevels(this.depth);
        this.resizeCanvas(this.canvas.offsetWidth, this.canvas.offsetHeight);

        this.canvas.addEventListener("contextmenu", (e) => {
            contextMenu.value.show(e);
            this.contextFrame = this.hlFrame
        });
        this.canvas.onmousemove = this.#onMouseMoveEvent();
        this.canvas.onmouseout = this.#onMouseOut();
        this.canvas.ondblclick = this.#onDoubleClick();
    }

    #onMouseMoveEvent() {
        return (event) => {
            const level = Math.floor((this.reversed ? event.offsetY : this.canvasHeight - event.offsetY) / Flamegraph.FRAME_HEIGHT);

            if (level >= 0 && level < this.levels.length) {
                let frame = this.#lookupFrame(level, event);
                this.hlFrame = frame

                if (frame) {
                    if (frame !== this.currentRoot) {
                        getSelection().removeAllRanges();
                    }

                    // if `contextFrame` != null, then context menu is selected.
                    if (this.contextFrame == null) {
                        this.hl.style.left = Math.max(this.#leftDistance(frame) - this.#leftDistance(this.currentRoot), 0) * this.pxPerSample + this.canvas.offsetLeft + 'px';
                        this.hl.style.width = Math.min(this.#totalValue(frame), this.#totalValue(this.currentRoot)) * this.pxPerSample + 'px';
                        this.hl.style.top = (this.reversed ? level * Flamegraph.FRAME_HEIGHT - this.currentScrollY : this.canvasHeight - (level + 1) * Flamegraph.FRAME_HEIGHT - this.currentScrollY) + this.canvas.offsetTop + 'px';
                        this.hl.firstChild.textContent = frame.title;
                        this.hl.style.display = 'block';
                    }

                    const tooltipContent = FlamegraphTooltips.generateTooltip(
                        this.eventType,
                        this.tooltipType,
                        this.useWeight,
                        frame,
                        this.levels[0][0].totalSamples,
                        this.levels[0][0].totalWeight)

                    this.flamegraphTooltip.showTooltip(event, this.currentScrollY, tooltipContent)

                    this.canvas.style.cursor = 'pointer';
                    this.canvas.onclick = () => {
                        if (frame !== this.currentRoot) {
                            this.#draw(frame, level, this.currentPattern);
                        }
                    };
                    return;
                }

                this.canvas.onmouseout();
            }
        };
    }

    #removeContextMenu() {
        this.closeContextMenu()
        this.contextMenu.value.hide()
    }

    closeContextMenu() {
        this.contextFrame = null
    }

    getHighlightedFrame() {
        return this.hlFrame
    }

    getContextFrame() {
        return this.contextFrame
    }

    updateScrollPositionY(value) {
        this.flamegraphTooltip.hideTooltip()
        this.#removeContextMenu()

        this.currentScrollY = value
    }

    removeHighlight() {
        // Don't remove highlighting if context menu is active
        if (this.contextFrame == null) {
            this.hl.style.display = 'none';
            this.canvas.title = '';
            this.canvas.style.cursor = '';
            this.canvas.onclick = '';
        }
    }

    removeAllHighlight() {
        let hl = document.getElementById("hl");
        if (hl != null) {
            hl.outerHTML = ""
        }
    }

    #onMouseOut() {
        return () => {
            this.removeHighlight()
            this.flamegraphTooltip.hideTooltip()
        };
    };

    #onDoubleClick() {
        return () => {
            getSelection().selectAllChildren(this.hl);
        };
    };

    #createHighlightDiv(canvas) {
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

    static #pct(a, b) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }

    #lookupFrame(level, event) {
        let frames = this.visibleFrames[level];
        for (let i = 0; i < frames.length; i++) {
            let visibleFrame = frames[i];

            if (Flamegraph.#pointInPath(visibleFrame.rect, event.offsetX, event.offsetY)) {
                return visibleFrame.frame;
            }
        }
    }

    static #pointInPath(rect, x, y) {
        let xPosition = x >= rect.x && x <= (rect.x + rect.width)
        let yPosition = y >= rect.y && y <= (y + rect.height)
        return xPosition && yPosition
    }

    resizeCanvas(width, height) {
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

    static initializeLevels(depth) {
        let levels = Array(depth + 1);
        for (let h = 0; h < levels.length; h++) {
            levels[h] = [];
        }
        return levels;
    }

    clearCanvas() {
        this.removeHighlight()
        this.context.fillStyle = '#ffffff';
        this.context.fillRect(0, 0, this.canvasWidth, this.canvasHeight);
    }

    drawRoot() {
        this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
    }

    reverse() {
        this.reversed = !this.reversed;
        this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
    }

    search(pattern) {
        this.currentPattern = RegExp(pattern);
        let highlighted = this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
        let highlightedTotal = this.#calculateHighlighted(highlighted);
        return Flamegraph.#pct(highlightedTotal, this.#totalValue(this.currentRoot));
    }

    resetSearch() {
        this.currentPattern = null;
        this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
    }

    resetZoom() {
        this.#draw(this.levels[0][0], 0, this.currentPattern);
    }

    #draw(root, rootLevel, pattern) {
        this.clearCanvas();
        this.visibleFrames = Flamegraph.initializeLevels(this.depth);

        this.currentRoot = root;
        this.currentRootLevel = rootLevel;

        this.pxPerSample = this.canvasWidth / this.#totalValue(root);

        const xStart = this.#leftDistance(root);
        const xEnd = xStart + this.#totalValue(root);
        const highlighted = []

        for (let level = 0; level < this.levels.length; level++) {
            const y = this.reversed ? level * Flamegraph.FRAME_HEIGHT : this.canvasHeight - (level + 1) * Flamegraph.FRAME_HEIGHT;
            const frames = this.levels[level];

            for (let i = 0; i < frames.length; i++) {
                let frame = frames[i];
                if (this.#frame_not_overflow(frame, xStart, xEnd)) {
                    let isHighlighted = this.#isMethodHighlighted(highlighted, frame, pattern);
                    let isUnderRoot = level < rootLevel;

                    const rectangle = this.#createRectangle(this.pxPerSample, frame, y, xStart);
                    this.visibleFrames[level].push({rect: rectangle, frame: frame});
                    this.#drawFrame(this.pxPerSample, frame, y, xStart, rectangle, isHighlighted, isUnderRoot);
                }
            }
        }

        return highlighted
    }

    #frame_not_overflow(frame, xStart, xEnd) {
        return this.#leftDistance(frame) < xEnd && this.#leftDistance(frame) + this.#totalValue(frame) > xStart;
    }

    #highlight(highlighted, frame) {
        return highlighted[this.#leftDistance(frame)] >= this.#totalValue(frame) || (highlighted[this.#leftDistance(frame)] = this.#totalValue(frame))
    }

    #isMethodHighlighted(highlighted, frame, pattern) {
        return pattern && frame.title.match(pattern) && this.#highlight(highlighted, frame);
    }

    #calculateHighlighted(highlighted) {
        let total = 0;
        let left = 0;
        Object.keys(highlighted)
            .sort(function (a, b) {
                return a - b;
            })
            .forEach(function (x) {
                if (+x >= left) {
                    total += highlighted[x];
                    left = +x + highlighted[x];
                }
            });
        return total;
    }

    static #toPath2D(rect) {
        const path = new Path2D()
        path.rect(rect.x, rect.y, rect.width, rect.height)
        return path;
    }

    #createRectangle(pxPerSample, frame, y, xStart) {
        const x = (this.#leftDistance(frame) - xStart) * pxPerSample;
        const width = this.#totalValue(frame) * pxPerSample;
        return {x: x, y: y, width: width, height: Flamegraph.FRAME_HEIGHT};
    }

    #drawFrame(pxPerSample, frame, y, xStart, rect, isHighlighted, isUnderRoot) {
        const path = Flamegraph.#toPath2D(rect)

        this.context.fillStyle = isHighlighted ? Flamegraph.HIGHLIGHTED_COLOR : this.#color(frame);
        this.context.strokeStyle = 'white';
        this.context.fill(path);
        this.context.lineWidth = 1;
        this.context.stroke(path);

        // Do we want to fill the text, or the frame is too small and leave it empty
        if (this.#totalValue(frame) * pxPerSample >= 21) {
            const chars = Math.floor((this.#totalValue(frame) * pxPerSample) / 7);
            const title = frame.title.length <= chars ? frame.title : frame.title.substring(0, chars - 2) + '..';
            this.context.fillStyle = '#000000';
            this.context.fillText(title, Math.max(this.#leftDistance(frame) - xStart, 0) * pxPerSample + 3, y + 14, this.#totalValue(frame) * pxPerSample - 6);
        }

        if (isUnderRoot) {
            this.context.fillStyle = 'rgba(255, 255, 255, 0.5)';
            this.context.fill(path);
        }
    }

    #color(frame) {
        if (this.useWeight) {
            return frame.colorWeight
        } else {
            return frame.colorSamples
        }
    }

    #totalValue(frame) {
        if (this.useWeight) {
            return frame.totalWeight
        } else {
            return frame.totalSamples
        }
    }

    #leftDistance(frame) {
        if (this.useWeight) {
            return frame.leftWeight
        } else {
            return frame.leftSamples
        }
    }
}
