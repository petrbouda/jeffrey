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

export default class ThreadGroup {
    segments: ThreadRectangle[] = [];
    start: number = -1;
    end: number = -1;

    constructor(firstSegment: any) {
        this.segments.push(firstSegment);
        this.start = firstSegment.rect.x;
        this.end = this.start + firstSegment.rect.width;
    }

    addSegment(segment: any): void {
        this.segments.push(segment);
        this.end = segment.end;
    }
}


