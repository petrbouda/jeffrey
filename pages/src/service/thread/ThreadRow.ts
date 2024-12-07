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

import Tooltip from "../tooltip/Tooltip";
import ThreadGroups from "../thread/ThreadGroups";
import Konva from "konva";
import ThreadTooltips from "../thread/ThreadTooltips";
import ThreadRowData from "./model/ThreadRowData";
import ThreadPeriod from "@/service/thread/model/ThreadPeriod";
import TooltipPosition from "@/service/tooltip/TooltipPosition";
import ThreadCommon from "@/service/thread/model/ThreadCommon";
import ThreadMetadata from "@/service/thread/model/ThreadMetadata";
import Vector2d = Konva.Vector2d;

export default class ThreadRow {
    static lifespanColor = 'rgb(96,175,96)'
    static parkedColor = 'rgb(198,193,193)'
    static blockedColor = 'rgb(236,204,116)'
    static waitingColor = 'rgb(134,173,225)'
    static sleepColor = 'rgb(65,126,228)'
    static socketReadColor = 'rgb(228,33,33)'
    static socketWriteColor = 'rgb(241,135,168)'
    static fileReadColor = 'rgb(215,33,228)'
    static fileWriteColor = 'rgb(210,132,236)'

    static readonly FRAME_HEIGHT: number = 20;

    private readonly konvaContainer: HTMLElement;
    private readonly threadPointerName: string;
    private readonly threadTooltip: Tooltip;

    private readonly threadCommon: ThreadCommon;
    private readonly threadMetadata: ThreadMetadata;
    private readonly threadRow: ThreadRowData;

    private stage: Konva.Stage;
    private threadPointer: HTMLElement;

    constructor(threadCommon: ThreadCommon, threadRow: ThreadRowData, canvasElementId: string) {
        this.threadCommon = threadCommon;
        this.threadMetadata = threadCommon.metadata;
        this.threadRow = threadRow;

        this.konvaContainer = document.getElementById(canvasElementId) as HTMLElement;
        this.stage = this.createStage();

        this.threadPointerName = canvasElementId + "-pointer";
        this.createHighlightDiv(this.konvaContainer, this.threadPointerName);
        this.threadPointer = document.getElementsByClassName(this.threadPointerName)[0] as HTMLElement;
        this.threadTooltip = new Tooltip(this.konvaContainer);

        this.konvaContainer.onmousemove = this.onMouseMoveEvent();
        this.konvaContainer.onmouseout = this.onMouseOut();
    }

    private onMouseMoveEvent(): (event: MouseEvent) => void {
        return (event: MouseEvent) => {
            const rect = this.konvaContainer.getBoundingClientRect();
            const x = Math.floor(event.clientX - rect.left);

            this.threadPointer.style.left = Math.floor(x + this.konvaContainer.offsetLeft) + 'px';
            this.threadPointer.style.top = Math.floor(this.konvaContainer.offsetTop) + 'px';
            this.threadPointer.style.display = 'block';
        };
    }

    private onMouseOut(): () => void {
        return () => {
            this.removeHighlight()
            this.removeTooltip()
        };
    }

    private removeHighlight(): void {
        this.threadPointer.style.display = 'none';
    }

    private removeTooltip(): void {
        this.threadTooltip.hideTooltip();
    }

    private createHighlightDiv(canvas: HTMLElement, threadPointerName: string): void {
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

    public draw(): void {
        const pxPerMillis = this.stage.width() / this.threadCommon.totalDuration;
        const threadInfo = this.threadRow.threadInfo

        let width: number = this.stage.width();
        const lifespanGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.lifespanColor)
        const parkedGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.parkedColor)
        const blockedGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.blockedColor)
        const waitingGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.waitingColor)
        const sleepGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.sleepColor)
        const socketReadGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.socketReadColor)
        const socketWriteGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.socketWriteColor)
        const fileReadGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.fileReadColor)
        const fileWriteGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.fileWriteColor)

        this.threadRow.lifespan.forEach((period: ThreadPeriod) => lifespanGroups.addPeriod(period));
        this.threadRow.parked.forEach((period: ThreadPeriod) => parkedGroups.addPeriod(period));
        this.threadRow.blocked.forEach((period: ThreadPeriod) => blockedGroups.addPeriod(period));
        this.threadRow.waiting.forEach((period: ThreadPeriod) => waitingGroups.addPeriod(period));
        this.threadRow.sleep.forEach((period: ThreadPeriod) => sleepGroups.addPeriod(period));
        this.threadRow.socketRead.forEach((period: ThreadPeriod) => socketReadGroups.addPeriod(period));
        this.threadRow.socketWrite.forEach((period: ThreadPeriod) => socketWriteGroups.addPeriod(period));
        this.threadRow.fileRead.forEach((period: ThreadPeriod) => fileReadGroups.addPeriod(period));
        this.threadRow.fileWrite.forEach((period: ThreadPeriod) => fileWriteGroups.addPeriod(period));

        this.stage.add(this.borderLayer());
        this.stage.add(lifespanGroups.createLayer());
        this.stage.add(parkedGroups.createLayer());
        this.stage.add(blockedGroups.createLayer());
        this.stage.add(waitingGroups.createLayer());
        this.stage.add(sleepGroups.createLayer());
        this.stage.add(socketReadGroups.createLayer());
        this.stage.add(socketWriteGroups.createLayer());
        this.stage.add(fileReadGroups.createLayer());
        this.stage.add(fileWriteGroups.createLayer());

        this.stage.on('mousemove', () => {
            const pos = this.stage.getPointerPosition() as Vector2d;

            let xPos = Math.floor(pos.x);
            const parkedRects = parkedGroups.selectRectangles(xPos)
            const blockedRects = blockedGroups.selectRectangles(xPos)
            const waitingRects = waitingGroups.selectRectangles(xPos)
            const sleepRects = sleepGroups.selectRectangles(xPos)
            const socketReadRects = socketReadGroups.selectRectangles(xPos)
            const socketWriteRects = socketWriteGroups.selectRectangles(xPos)
            const fileReadRects = fileReadGroups.selectRectangles(xPos)
            const fileWriteRects = fileWriteGroups.selectRectangles(xPos)
            const totalRects =
                parkedRects.length
                + blockedRects.length
                + waitingRects.length
                + sleepRects.length
                + socketReadRects.length
                + socketWriteRects.length
                + fileReadRects.length
                + fileWriteRects.length

            if (totalRects > 0) {
                let tooltipContent = ThreadTooltips.header(threadInfo.javaName)
                if (parkedRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.parked, parkedRects, ThreadRow.parkedColor)
                }
                if (blockedRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.blocked, blockedRects, ThreadRow.blockedColor)
                }
                if (waitingRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.waiting, waitingRects, ThreadRow.waitingColor)
                }
                if (sleepRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.sleep, sleepRects, ThreadRow.sleepColor)
                }
                if (socketReadRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.socketRead, socketReadRects, ThreadRow.socketReadColor)
                }
                if (socketWriteRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.socketWrite, socketWriteRects, ThreadRow.socketWriteColor)
                }
                if (fileReadRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.fileRead, fileReadRects, ThreadRow.fileReadColor)
                }
                if (fileWriteRects.length > 0) {
                    tooltipContent = tooltipContent + ThreadTooltips.basic(
                        this.threadMetadata.fileWrite, fileWriteRects, ThreadRow.fileWriteColor)
                }
                this.threadTooltip.showTooltip(new TooltipPosition(pos.x, pos.y), 0, tooltipContent)
            } else {
                this.removeTooltip()
            }
        });
    }

    public resizeCanvas() {
        this.stage = this.createStage();
        this.draw();
    }

    private createStage(): Konva.Stage {
        return new Konva.Stage({
            container: this.konvaContainer.id,
            width: this.konvaContainer.offsetWidth,
            height: ThreadRow.FRAME_HEIGHT,
        });
    }

    private borderLayer(): Konva.Layer {
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
        this.removeTooltip()
    }
}
