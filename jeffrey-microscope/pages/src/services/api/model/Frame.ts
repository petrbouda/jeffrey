/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import FramePosition from '@/services/api/model/FramePosition';
import FrameSampleTypes from '@/services/api/model/FrameSampleTypes';
import DiffDetails from '@/services/api/model/DiffDetails';

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
    // Only set for TRUNCATED_SYNTHETIC frames - count of direct children pruned at this level
    public prunedChildrenCount?: number,
    // The frame's class is a JVM hidden class (JEP 371) - a lambda proxy, a method-handle form,
    // an indified string concatenation. Such names carry the JVM's address and are redrawn on
    // every run, so the address is stripped before the name ever reaches the UI.
    public hidden?: boolean
  ) {}
}
