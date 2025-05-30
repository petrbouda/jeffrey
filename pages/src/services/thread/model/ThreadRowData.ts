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

import ThreadInfo from "./ThreadInfo";
import ThreadPeriod from "@/services/thread/model/ThreadPeriod";

export default class ThreadRowData {
    constructor(
        public eventsCount: number,
        public totalDuration: number,
        public threadInfo: ThreadInfo,
        public lifespan: ThreadPeriod[],
        public parked: ThreadPeriod[],
        public blocked: ThreadPeriod[],
        public waiting: ThreadPeriod[],
        public sleep: ThreadPeriod[],
        public socketRead: ThreadPeriod[],
        public socketWrite: ThreadPeriod[],
        public fileRead: ThreadPeriod[],
        public fileWrite: ThreadPeriod[]) {
    }
}
