/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

/**
 * One level of a flamegraph: its frames left to right, held column by column, frame `i` at index
 * `i` of every column. A column holds what drawing, hit-testing and search read on every frame; the
 * fields only a tooltip reads stay out of them (see {@link FlamegraphData.frame}).
 *
 * Samples and weights are doubles: exact up to 2^53, which no profile reaches.
 */
export default class FrameLevel {
  readonly length: number;

  readonly leftSamples: Float64Array;
  readonly totalSamples: Float64Array;
  readonly leftWeight: Float64Array;
  readonly totalWeight: Float64Array;
  /** Index into {@link FlamegraphData.titles}. */
  readonly titleIndex: Int32Array;
  /** The frame type as its flamegraph.proto enum number (see `FrameType.fromProtoNumber`). */
  readonly type: Uint8Array;
  /** Children pruned below a TRUNCATED_SYNTHETIC frame; 0 on every other frame. */
  readonly prunedChildren: Int32Array;
  /** 1 for a frame of a differential graph, which carries the two deltas below. */
  readonly hasDiff: Uint8Array;
  /** Primary minus secondary samples, signed. */
  readonly diffSamples: Float64Array;
  /** Primary minus secondary weight, signed. */
  readonly diffWeight: Float64Array;

  constructor(length: number) {
    this.length = length;
    this.leftSamples = new Float64Array(length);
    this.totalSamples = new Float64Array(length);
    this.leftWeight = new Float64Array(length);
    this.totalWeight = new Float64Array(length);
    this.titleIndex = new Int32Array(length);
    this.type = new Uint8Array(length);
    this.prunedChildren = new Int32Array(length);
    this.hasDiff = new Uint8Array(length);
    this.diffSamples = new Float64Array(length);
    this.diffWeight = new Float64Array(length);
  }
}
