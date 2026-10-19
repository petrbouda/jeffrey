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

/** A frame of a graph's columns: the frame at `index` of `level`. */
export default class FrameRef {
  static readonly ROOT = new FrameRef(0, 0);

  readonly level: number;
  readonly index: number;

  constructor(level: number, index: number) {
    this.level = level;
    this.index = index;
  }

  equals(other: FrameRef | null): boolean {
    return other !== null && this.level === other.level && this.index === other.index;
  }
}
