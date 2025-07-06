/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import GCGenerationType from "@/services/profile/custom/gc/GCGenerationType.ts";

export default class GCEvent {
    constructor(
        public timestamp: number,
        public gcId: number,
        public generationType: GCGenerationType,
        public generation: string,
        public type: string,
        public cause: string,
        public duration: number,
        public beforeGC: number,
        public afterGC: number,
        public freed: number,
        public efficiency: number,
        public heapSize: number,
        public sumOfPauses: number,
        public longestPause: number
    ) {}
}
