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

import ThreadSegment from "@/service/thread/ThreadSegment";
import ThreadGroup from "@/service/thread/ThreadGroup";
import Konva from "konva";
import ThreadRow from "@/service/thread/ThreadRow";

export default class ThreadGroups {

    static MIN_WIDTH = 1;

    segments = []

    constructor(konvaStage, eventLabel, threadInfo, threadTooltip, pxPerMillis, color) {
        this.konvaStage = konvaStage
        this.eventLabel = eventLabel
        this.threadInfo = threadInfo
        this.threadTooltip = threadTooltip
        this.totalWidth = konvaStage.width
        this.color = color
        this.pxPerMillis = pxPerMillis
    }

    addEvent(event) {
        const rect = this.#createRectangle(event.startOffset, event.width);
        const segment = new ThreadSegment(rect, event);
        this.segments.push(segment);
    }

    #getAggregatedGroups() {
        this.segments.sort((a, b) => a.rect.start - b.rect.start);
        const groups = [];

        for (let [index, currSegment] of this.segments.entries()) {
            if (index === 0) {
                groups.push(new ThreadGroup(currSegment));
                continue;
            }

            const latestGroup = groups[groups.length - 1];
            if (latestGroup.end >= (currSegment.rect.x - 1)) {
                latestGroup.addSegment(currSegment);
            } else {
                groups.push(new ThreadGroup(currSegment));
            }
        }

        return groups
    }

    createLayer() {
        const layer = new Konva.Layer();
        this.#getAggregatedGroups().forEach((group) => {
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

    selectSegments(xPos) {
        this.segments.sort((a, b) => a.rect.start - b.rect.start);

        const selectedSegments = [];
        for (let segment of this.segments) {
            if (segment.start <= xPos && segment.end > xPos) {
                selectedSegments.push(segment);
            }
        }
        return selectedSegments;
    }

    #createRectangle(startOffset, width) {
        let x = Math.floor(startOffset * this.pxPerMillis)
        // if the activity is at the end of the thread, the x value can be equal to the canvas width (not visible)
        // in this case, we need to decrease the x value by 1 to make the activity visible, e.g. Shutdown hook Thread
        if (x === this.totalWidth) {
            x = x - (ThreadGroups.MIN_WIDTH + 1);
        }

        const calculatedWidth = Math.round(width * this.pxPerMillis);
        return {
            x: Math.max(x, 1),
            y: 0,
            width: Math.max(calculatedWidth, ThreadGroups.MIN_WIDTH),
            height: ThreadRow.FRAME_HEIGHT
        };
    }
}
