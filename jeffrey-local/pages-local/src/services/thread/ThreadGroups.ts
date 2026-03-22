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

import ThreadRectangle from "./ThreadRectangle";
import Konva from "konva";
import ThreadRow from "./ThreadRow";
import Rectangle from "./Rectangle";
import ThreadPeriod from "@/services/thread/model/ThreadPeriod";

export default class ThreadGroups {
    static readonly MIN_WIDTH = 1;

    private rectangles: ThreadRectangle[] = [];

    constructor(
        private totalWidth: number,
        private pxPerMillis: number,
        private color: string,
    ) {
    }

    public addPeriod(period: ThreadPeriod): void {
        const rect = this.createRectangle(period);
        const rectangle = new ThreadRectangle(rect, period);
        this.rectangles.push(rectangle);
    }

    public createLayer(): Konva.Layer {
        this.rectangles.sort((a, b) => a.start - b.start);

        const layer = new Konva.Layer();
        this.rectangles.forEach((group) => {
            const konvaRect = new Konva.Rect({
                x: group.start,
                y: 0,
                width: group.end - group.start,
                height: ThreadRow.FRAME_HEIGHT,
                fill: this.color,
            });

            layer.add(konvaRect);
        });
        return layer;
    }

    selectRectangles(xPos: number): ThreadRectangle[] {
        this.rectangles.sort((a, b) => a.start - b.start);

        const selectedSegments: ThreadRectangle[] = [];
        for (let segment of this.rectangles) {
            if (segment.start <= xPos && segment.end > xPos) {
                selectedSegments.push(segment);
            }
        }
        return selectedSegments;
    }

    private createRectangle(period: ThreadPeriod): Rectangle {
        let x = Math.floor(period.startOffset * this.pxPerMillis);
        // if the activity is at the end of the thread, the x value can be equal to the canvas width (not visible)
        // in this case, we need to decrease the x value by 1 to make the activity visible, e.g. Shutdown hook Thread
        if (x === this.totalWidth) {
            x = x - (ThreadGroups.MIN_WIDTH + 1);
        }

        const calculatedWidth = Math.round(period.width * this.pxPerMillis);
        return new Rectangle(x, 0, Math.max(calculatedWidth, ThreadGroups.MIN_WIDTH), ThreadRow.FRAME_HEIGHT);
    }
}
