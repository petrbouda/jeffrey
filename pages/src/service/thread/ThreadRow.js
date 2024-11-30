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

import Tooltip from "@/service/Tooltip";
import ThreadGroups from "@/service/thread/ThreadGroups";
import Konva from "konva";
import ThreadTooltips from "@/service/thread/ThreadTooltips";

export default class ThreadRow {

    static FRAME_HEIGHT = 20;

    active = false

    tooltipTimeoutId = null
    currentScrollY = 0

    constructor(duration, data, canvasElementId) {
        this.canvasElementId = canvasElementId;
        this.duration = duration;
        this.data = data;

        this.konvaContainer = document.getElementById(canvasElementId);
        this.stage = this.stage = this.#createStage(this.konvaContainer.offsetWidth);

        this.threadPointerName = canvasElementId + "-pointer"
        this.#createHighlightDiv(this.konvaContainer, this.threadPointerName)
        this.threadPointer = document.getElementsByClassName(this.threadPointerName)[0];
        this.threadTooltip = new Tooltip(this.konvaContainer)

        this.konvaContainer.onmousemove = this.#onMouseMoveEvent();
        this.konvaContainer.onmouseout = this.#onMouseOut();
    }

    #onMouseMoveEvent() {
        return (event) => {
            const rect = this.konvaContainer.getBoundingClientRect();
            const x = Math.floor(event.clientX - rect.left);

            this.threadPointer.style.left = Math.floor(x + this.konvaContainer.offsetLeft) + 'px';
            this.threadPointer.style.top = Math.floor(this.konvaContainer.offsetTop) + 'px';
            this.threadPointer.style.display = 'block';
        };
    }

    #onMouseOut() {
        return () => {
            this.#removeHighlight()
            this.#removeTooltip()
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

    draw() {
        const pxPerMillis = this.stage.width() / this.duration;
        const threadInfo = this.data.threadInfo

        const lifespanGroups = new ThreadGroups(
            this.stage, this.data.lifespan.label, threadInfo, this.threadTooltip, pxPerMillis, 'rgba(0,160,0,0.7)')
        const parkedGroups = new ThreadGroups(
            this.stage, this.data.parked.label, threadInfo, this.threadTooltip, pxPerMillis, 'rgb(198,193,193)')
        const blockedGroups = new ThreadGroups(
            this.stage, this.data.blocked.label, threadInfo, this.threadTooltip, pxPerMillis, 'rgb(236,204,116)')
        const waitingGroups = new ThreadGroups(
            this.stage, this.data.waiting.label, threadInfo, this.threadTooltip, pxPerMillis, 'rgb(91,144,223)')

        this.data.lifespan.periods.forEach((event) => {
            lifespanGroups.addEvent(event)
        })

        this.data.parked.periods.forEach((event) => {
            parkedGroups.addEvent(event)
        });

        this.data.blocked.periods.forEach((event) => {
            blockedGroups.addEvent(event)
        });

        this.data.waiting.periods.forEach((event) => {
            waitingGroups.addEvent(event)
        });

        this.stage.add(this.#borderLayer());
        this.stage.add(lifespanGroups.createLayer());
        this.stage.add(parkedGroups.createLayer());
        this.stage.add(blockedGroups.createLayer());
        this.stage.add(waitingGroups.createLayer());

        this.stage.on('mousemove', () => {
            const pos = this.stage.getPointerPosition();

            let xPos = Math.floor(pos.x);
            const parkedSegments = parkedGroups.selectSegments(xPos)
            const blockedSegments = blockedGroups.selectSegments(xPos)
            const waitingSegments = waitingGroups.selectSegments(xPos)
            const totalSegments = parkedSegments.length + blockedSegments.length + waitingSegments.length

            if (totalSegments > 0) {
                let tooltipContent = ThreadTooltips.header(threadInfo)
                if (parkedSegments.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(this.data.parked.label, parkedSegments)
                }
                if (blockedSegments.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(this.data.blocked.label, blockedSegments)
                }
                if (waitingSegments.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(this.data.waiting.label, waitingSegments)
                }
                this.threadTooltip.showTooltip({offsetX: xPos, offsetY: pos.y}, 0, tooltipContent)
            } else {
                this.#removeTooltip()
            }
        });
    }

    resizeCanvas(width) {
        this.stage = this.#createStage(width);
        this.draw();
    }

    #createStage(width) {
        return new Konva.Stage({
            container: this.canvasElementId,
            width: width,
            height: ThreadRow.FRAME_HEIGHT,
        });
    }

    #borderLayer() {
        const borderLayer = new Konva.Layer();
        borderLayer.add(new Konva.Rect({
            x: 0,
            y: 0,
            width: this.stage.width(),
            height: ThreadRow.FRAME_HEIGHT,
            stroke: 'black',
            strokeWidth: 1
        }));
        return borderLayer
    }

    onWindowScroll() {
        this.#removeTooltip()
    }
}
