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

import Frame from '@/services/api/model/Frame';
import FrameLevel from '@/services/api/model/FrameLevel';
import FramePosition from '@/services/api/model/FramePosition';
import FrameSampleTypes from '@/services/api/model/FrameSampleTypes';
import DiffDetails from '@/services/api/model/DiffDetails';
import FrameType from '@/services/flamegraphs/FrameType';

/** The fields of a frame only a tooltip reads, resolved for one frame when it is shown. */
export interface FrameDetails {
  selfSamples?: number;
  position?: FramePosition;
  sampleTypes?: FrameSampleTypes;
  diffDetails?: DiffDetails;
  hidden?: boolean;
}

/** Resolves the details of the frame at `index` of `level`. */
export type FrameDetailsLookup = (level: number, index: number) => FrameDetails;

/**
 * A flamegraph held in columns: per level a {@link FrameLevel}, the titles once each in a pool the
 * frames index into, and the tooltip-only fields kept out of the columns and resolved on demand. A
 * graph of tens of thousands of frames is then a few typed arrays instead of an object per frame,
 * and anything done per title (parsing it into a label, testing it against a search) is done once
 * per title.
 *
 * Level 0 always holds the root frame.
 */
export default class FlamegraphData {
  readonly depth: number;
  readonly levels: readonly FrameLevel[];
  readonly titles: readonly string[];
  private readonly detailsOf: FrameDetailsLookup;

  constructor(
    depth: number,
    levels: readonly FrameLevel[],
    titles: readonly string[],
    detailsOf: FrameDetailsLookup
  ) {
    this.depth = depth;
    this.levels = levels;
    this.titles = titles;
    this.detailsOf = detailsOf;
  }

  /** A graph built from frame objects, level by level; for fixtures and tests. */
  static fromFrames(frames: Frame[][]): FlamegraphData {
    const titles: string[] = [];
    const titleIndexes = new Map<string, number>();
    const levels = frames.map(levelFrames => {
      const level = new FrameLevel(levelFrames.length);
      levelFrames.forEach((frame, i) => {
        let titleIndex = titleIndexes.get(frame.title);
        if (titleIndex === undefined) {
          titleIndex = titles.length;
          titles.push(frame.title);
          titleIndexes.set(frame.title, titleIndex);
        }
        level.leftSamples[i] = frame.leftSamples;
        level.totalSamples[i] = frame.totalSamples;
        level.leftWeight[i] = frame.leftWeight ?? 0;
        level.totalWeight[i] = frame.totalWeight ?? 0;
        level.titleIndex[i] = titleIndex;
        level.type[i] = FrameType.toProtoNumber(frame.type);
        level.prunedChildren[i] = frame.prunedChildrenCount ?? 0;
        if (frame.diffDetails) {
          level.hasDiff[i] = 1;
          level.diffSamples[i] = frame.diffDetails.samples;
          level.diffWeight[i] = frame.diffDetails.weight;
        }
      });
      return level;
    });
    return new FlamegraphData(frames.length, levels, titles, (level, index) => {
      const frame = frames[level][index];
      return {
        selfSamples: frame.selfSamples,
        position: frame.position,
        sampleTypes: frame.sampleTypes,
        diffDetails: frame.diffDetails,
        hidden: frame.hidden
      };
    });
  }

  title(level: number, index: number): string {
    return this.titles[this.levels[level].titleIndex[index]] ?? '';
  }

  type(level: number, index: number): string {
    return FrameType.fromProtoNumber(this.levels[level].type[index]);
  }

  /**
   * The frame at `index` of `level` as an object, with every field: for the one frame a tooltip, a
   * root header or the context menu shows.
   */
  frame(level: number, index: number): Frame {
    const columns = this.levels[level];
    const details = this.detailsOf(level, index);
    return new Frame(
      columns.leftSamples[index],
      columns.totalSamples[index],
      this.title(level, index),
      this.type(level, index),
      columns.leftWeight[index],
      columns.totalWeight[index],
      details.selfSamples || undefined,
      details.position,
      details.sampleTypes,
      details.diffDetails,
      columns.prunedChildren[index] || undefined,
      details.hidden || undefined
    );
  }
}
