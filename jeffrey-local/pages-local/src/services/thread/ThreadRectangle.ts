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

import Rectangle from "./Rectangle";
import ThreadPeriod from "@/services/thread/model/ThreadPeriod";

export default class ThreadRectangle {
    rect: Rectangle;
    period: ThreadPeriod;
    start: number;
    end: number;

    constructor(rect: Rectangle, period: ThreadPeriod) {
        this.rect = rect;
        this.period = period;
        this.start = this.rect.x;
        this.end = this.rect.x + this.rect.width;
    }
}

