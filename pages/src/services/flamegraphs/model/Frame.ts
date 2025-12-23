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
        public leftSamples: number,
        public totalSamples: number,
        public title: string,
        public type: string,
        // Optional fields - omitted from JSON when zero/null to reduce transfer size
        public leftWeight?: number,
        public totalWeight?: number,
        public selfSamples?: number,
        public position?: FramePosition,
        public sampleTypes?: FrameSampleTypes,
        public diffDetails?: DiffDetails,
        // For guardian analysis - frames before marker are shown in grey
        public beforeMarker?: boolean) {
    }
}
