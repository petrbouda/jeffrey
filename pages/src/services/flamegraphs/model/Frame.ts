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

import FramePosition from "@/services/flamegraphs/model/FramePosition";
import FrameSampleTypes from "@/services/flamegraphs/model/FrameSampleTypes";
import DiffDetails from "@/services/flamegraphs/model/DiffDetails";

export default class Frame {
    constructor(
        public colorSamples: string,
        public colorWeight: string,
        public leftSamples: number,
        public leftWeight: number,
        public selfSamples: number,
        public selfWeight: number,
        public totalSamples: number,
        public totalWeight: number,
        public position: FramePosition,
        public sampleTypes: FrameSampleTypes,
        public title: string,
        public type: string,
        public typeTitle: string,
        public diffDetails: DiffDetails) {
    }
}
