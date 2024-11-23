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

import ThreadTooltips from "@/service/thread/ThreadTooltips";
import Tooltip from "@/service/Tooltip";

export default class ThreadRow {

    static FRAME_HEIGHT = 20;
    static MIN_WIDTH = 1;

    pxPerMillis = 0;

    active = false

    tooltipTimeoutId = null
    currentScrollY = 0

    constructor(duration, data, canvasElementId) {
        this.duration = duration;
        this.data = data;
        this.canvas = document.getElementById(canvasElementId);
        this.canvas.width = this.canvas.offsetWidth;
        this.canvas.height = ThreadRow.FRAME_HEIGHT;
        this.context = this.canvas.getContext('2d');

        this.pxPerMillis = this.canvas.width / this.duration;

        this.threadPointerName = canvasElementId + "-pointer"
        this.#createHighlightDiv(this.canvas, this.threadPointerName)
        this.threadPointer = document.getElementsByClassName(this.threadPointerName)[0];
        this.threadTooltip = new Tooltip(this.canvas)

        this.canvas.onmousemove = this.#onMouseMoveEvent();
        this.canvas.onmouseout = this.#onMouseOut();
    }

    #onMouseMoveEvent() {
        return (event) => {
            const rect = this.canvas.getBoundingClientRect();
            const x = event.clientX - rect.left;

            this.threadPointer.style.left = Math.round(x + this.canvas.offsetLeft) + 'px';
            this.threadPointer.style.top = Math.round(this.canvas.offsetTop) + 'px';
            this.threadPointer.style.display = 'block';
            this.canvas.style.cursor = 'pointer';

            const tooltipContent = this.#generateTooltipTable(null)
            this.threadTooltip.showTooltip(event, this.currentScrollY, tooltipContent)
        };
    }

    #onMouseOut() {
        return () => {
            this.#removeHighlight()
            this.threadTooltip.hideTooltip()
        };
    };

    #removeHighlight() {
        this.threadPointer.style.display = 'none';
    }

    #removeTooltip() {
        this.threadTooltip.hideTooltip()
    }

    #createHighlightDiv(canvas, threadPointerName) {
        canvas.insertAdjacentHTML(
            'afterend',
            '<div class="' + threadPointerName + '" style="' +
            ' position: absolute;' +
            ' display: none;' +
            ' overflow: hidden;' +
            ' white-space: nowrap;' +
            ' pointer-events: none;' +
            ' background-color: black;' +
            ' width: 1px;' +
            ' height: ' + ThreadRow.FRAME_HEIGHT + 'px;"></div>'
        )
    }

    #generateTooltipTable(threadData) {
        return ThreadTooltips.generateTooltip(threadData)
    }

    draw() {
        this.clearCanvas()

        // Draw the active period - the green rectangles with Start and End time
        this.data.lifespan.forEach((event) => {
            const rect = this.#createRectangle(event.startOffset, event.endOffset - event.startOffset);
            const path = ThreadRow.#toPath2D(rect)

            this.context.fillStyle = 'rgba(0,160,0,0.7)';
            this.context.fill(path);
        })

        this.data.parked.forEach((event) => {
            const rect = this.#createRectangle(event.startOffset, event.width);
            const path = ThreadRow.#toPath2D(rect)
            this.context.fillStyle = 'rgb(198,193,193)';
            this.context.fill(path);
        });

        this.data.blocked.forEach((event) => {
            const rect = this.#createRectangle(event.startOffset, event.width);
            const path = ThreadRow.#toPath2D(rect)
            this.context.fillStyle = 'rgb(236,204,116)';
            this.context.fill(path);
        });

        this.data.waiting.forEach((event) => {
            const rect = this.#createRectangle(event.startOffset, event.width);
            const path = ThreadRow.#toPath2D(rect)
            this.context.fillStyle = 'rgb(91,144,223)';
            this.context.fill(path);
        });
    }

    resizeCanvas(width) {
        this.canvas.width = width;
        this.pxPerMillis = this.canvas.width / this.duration;
        this.draw();
    }

    clearCanvas() {
        this.context.fillStyle = '#ffffff';
        this.context.fillRect(0, 0, this.canvas.width, this.canvas.height);
        this.context.strokeRect(0, 0, this.canvas.width, this.canvas.height);
    }

    onWindowScroll() {
        this.#removeTooltip()
    }

    #createRectangle(startOffset, width) {
        let x = Math.round(startOffset * this.pxPerMillis)
        // if the activity is at the end of the thread, the x value can be equal to the canvas width (not visible)
        // in this case, we need to decrease the x value by 1 to make the activity visible, e.g. Shutdown hook Thread
        if (x === this.canvas.width) {
            x = x - (ThreadRow.MIN_WIDTH + 1);
        }

        const calculatedWidth = width * this.pxPerMillis;
        return {
            x: Math.max(x, 1),
            y: 0,
            width: Math.max(calculatedWidth, ThreadRow.MIN_WIDTH),
            height: ThreadRow.FRAME_HEIGHT
        };
    }

    #drawVerticalLine(x, color) {
        this.context.strokeStyle = color;
        this.context.moveTo(x, 0);
        this.context.lineTo(x, this.canvas.height);
        this.context.stroke();
    }

    static #toPath2D(rect) {
        const path = new Path2D()
        path.rect(rect.x, rect.y, rect.width, rect.height)
        return path;
    }
}
