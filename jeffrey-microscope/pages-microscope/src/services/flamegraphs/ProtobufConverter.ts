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

import BothGraphData from '@/services/api/model/BothGraphData';
import FlamegraphData, { type FrameDetails } from '@/services/api/model/FlamegraphData';
import FrameLevel from '@/services/api/model/FrameLevel';
import FramePosition from '@/services/api/model/FramePosition';
import FrameSampleTypes from '@/services/api/model/FrameSampleTypes';
import DiffDetails from '@/services/api/model/DiffDetails';
import ProtoWireReader, { WireType } from '@/services/flamegraphs/ProtoWireReader';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import Serie from '@/services/timeseries/model/Serie';

// Field numbers of flamegraph.proto (profiles/flamegraph/src/main/proto). ProtobufConverter.test
// encodes every field with the code generated from that file and checks this decoder reads it back.
const GRAPH_DATA = { FLAMEGRAPH: 1, TIMESERIES: 2 } as const;
const FLAMEGRAPH_DATA = { DEPTH: 1, LEVELS: 2, TITLE_POOL: 3 } as const;
const LEVEL = { FRAMES: 1 } as const;
const FRAME = {
  LEFT_SAMPLES: 1,
  TOTAL_SAMPLES: 2,
  TITLE_INDEX: 3,
  TYPE: 4,
  LEFT_WEIGHT: 5,
  TOTAL_WEIGHT: 6,
  SELF_SAMPLES: 7,
  POSITION: 8,
  SAMPLE_TYPES: 9,
  DIFF_DETAILS: 10,
  PRUNED_CHILDREN_COUNT: 11,
  HIDDEN: 12
} as const;
const FRAME_POSITION = { BCI: 1, LINE: 2 } as const;
const FRAME_SAMPLE_TYPES = { INLINED: 1, C1: 2, INTERPRET: 3, JIT: 4 } as const;
const DIFF_DETAILS = {
  SAMPLES: 1,
  WEIGHT: 2,
  PERCENT_SAMPLES: 3,
  PERCENT_WEIGHT: 4,
  SECONDARY_SAMPLES: 5,
  SECONDARY_WEIGHT: 6
} as const;
const TIMESERIES_DATA = { SERIES: 1 } as const;
const TIMESERIES_SERIES = { NAME: 1, DATA: 2 } as const;
const TIMESERIES_POINT = { TIMESTAMP: 1, VALUE: 2 } as const;

const FIELD_NUMBER_SHIFT = 3;
// The frame type column is a Uint8Array
const MAX_FRAME_TYPE_NUMBER = 255;
const WIRE_TYPE_MASK = 7;

/** Where each frame's message sits in the response, to read its tooltip-only fields later. */
interface FrameSpans {
  start: Uint32Array;
  end: Uint32Array;
}

/**
 * Decodes a graph response, the GraphData message of flamegraph.proto, straight from its bytes: the
 * flamegraph into the columns of {@link FlamegraphData}, without an object per frame on the way,
 * and the timeseries into {@link TimeseriesData}.
 *
 * The fields only a tooltip reads (self samples, source position, compilation types, the full diff
 * details, the hidden-class mark) are not decoded up front. The graph keeps the response bytes and
 * the span of every frame's message, and reads those fields for the one frame a tooltip shows.
 *
 * A field number this decoder does not know, or one with an unexpected wire type, is skipped, as
 * any protobuf reader does.
 */
export default class ProtobufConverter {
  static decode(data: ArrayBuffer): BothGraphData {
    const bytes = new Uint8Array(data);
    const reader = new ProtoWireReader(bytes);
    let flamegraph: FlamegraphData | null = null;
    let timeseries: TimeseriesData | null = null;

    while (reader.pos < bytes.length) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (field === GRAPH_DATA.FLAMEGRAPH && wireType === WireType.LENGTH_DELIMITED) {
        flamegraph = this.readFlamegraph(reader, reader.delimitedEnd());
      } else if (field === GRAPH_DATA.TIMESERIES && wireType === WireType.LENGTH_DELIMITED) {
        timeseries = this.readTimeseries(reader, reader.delimitedEnd());
      } else {
        reader.skip(wireType);
      }
    }
    return new BothGraphData(flamegraph, timeseries);
  }

  private static readFlamegraph(reader: ProtoWireReader, end: number): FlamegraphData {
    let depth = 0;
    const levels: FrameLevel[] = [];
    const spans: FrameSpans[] = [];
    const titles: string[] = [];

    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (field === FLAMEGRAPH_DATA.DEPTH && wireType === WireType.VARINT) {
        depth = reader.int32();
      } else if (field === FLAMEGRAPH_DATA.LEVELS && wireType === WireType.LENGTH_DELIMITED) {
        const levelEnd = reader.delimitedEnd();
        const frameCount = this.countFrames(reader, levelEnd);
        const level = new FrameLevel(frameCount);
        const levelSpans = { start: new Uint32Array(frameCount), end: new Uint32Array(frameCount) };
        this.readLevel(reader, levelEnd, level, levelSpans);
        levels.push(level);
        spans.push(levelSpans);
      } else if (field === FLAMEGRAPH_DATA.TITLE_POOL && wireType === WireType.LENGTH_DELIMITED) {
        titles.push(reader.string());
      } else {
        reader.skip(wireType);
      }
    }

    // Every flamegraph the server builds has its root frame on level 0, even one of no samples
    if (levels.length === 0 || levels[0].length === 0) {
      throw new Error('Flamegraph response carries no root frame');
    }

    const bytes = reader.bytes;
    return new FlamegraphData(depth, levels, titles, (level, index) =>
      this.readDetails(
        new ProtoWireReader(bytes, spans[level].start[index]),
        spans[level].end[index]
      )
    );
  }

  /** The number of frames in a level, for sizing its columns; leaves the reader where it was. */
  private static countFrames(reader: ProtoWireReader, end: number): number {
    const start = reader.pos;
    let count = 0;
    while (reader.pos < end) {
      const tag = reader.uint32();
      const wireType = tag & WIRE_TYPE_MASK;
      if (tag >>> FIELD_NUMBER_SHIFT === LEVEL.FRAMES && wireType === WireType.LENGTH_DELIMITED) {
        count++;
      }
      reader.skip(wireType);
    }
    reader.pos = start;
    return count;
  }

  private static readLevel(
    reader: ProtoWireReader,
    end: number,
    level: FrameLevel,
    spans: FrameSpans
  ): void {
    let index = 0;
    while (reader.pos < end) {
      const tag = reader.uint32();
      const wireType = tag & WIRE_TYPE_MASK;
      if (tag >>> FIELD_NUMBER_SHIFT === LEVEL.FRAMES && wireType === WireType.LENGTH_DELIMITED) {
        const frameEnd = reader.delimitedEnd();
        spans.start[index] = reader.pos;
        spans.end[index] = frameEnd;
        this.readFrameColumns(reader, frameEnd, level, index);
        index++;
      } else {
        reader.skip(wireType);
      }
    }
  }

  /** Reads the fields of a frame the columns hold, skipping the tooltip-only ones. */
  private static readFrameColumns(
    reader: ProtoWireReader,
    end: number,
    level: FrameLevel,
    index: number
  ): void {
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (wireType === WireType.VARINT) {
        switch (field) {
          case FRAME.LEFT_SAMPLES:
            level.leftSamples[index] = reader.int64();
            continue;
          case FRAME.TOTAL_SAMPLES:
            level.totalSamples[index] = reader.int64();
            continue;
          case FRAME.TITLE_INDEX:
            level.titleIndex[index] = reader.int32();
            continue;
          case FRAME.TYPE:
            level.type[index] = this.frameTypeNumber(reader.int32());
            continue;
          case FRAME.LEFT_WEIGHT:
            level.leftWeight[index] = reader.int64();
            continue;
          case FRAME.TOTAL_WEIGHT:
            level.totalWeight[index] = reader.int64();
            continue;
          case FRAME.PRUNED_CHILDREN_COUNT:
            level.prunedChildren[index] = reader.int32();
            continue;
        }
      } else if (field === FRAME.DIFF_DETAILS && wireType === WireType.LENGTH_DELIMITED) {
        // The two deltas colour every frame of a differential graph, so they are columns too
        const diff = this.readDiffDetails(reader, reader.delimitedEnd());
        level.hasDiff[index] = 1;
        level.diffSamples[index] = diff.samples;
        level.diffWeight[index] = diff.weight;
        continue;
      }
      reader.skip(wireType);
    }
  }

  /** A type number past what the column holds is one this client does not know: UNKNOWN (0). */
  private static frameTypeNumber(value: number): number {
    return value >= 0 && value <= MAX_FRAME_TYPE_NUMBER ? value : 0;
  }

  /** Reads the tooltip-only fields of one frame message. */
  private static readDetails(reader: ProtoWireReader, end: number): FrameDetails {
    const details: FrameDetails = {};
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (field === FRAME.SELF_SAMPLES && wireType === WireType.VARINT) {
        details.selfSamples = reader.int64();
      } else if (field === FRAME.HIDDEN && wireType === WireType.VARINT) {
        details.hidden = reader.bool();
      } else if (field === FRAME.POSITION && wireType === WireType.LENGTH_DELIMITED) {
        details.position = this.readPosition(reader, reader.delimitedEnd());
      } else if (field === FRAME.SAMPLE_TYPES && wireType === WireType.LENGTH_DELIMITED) {
        details.sampleTypes = this.readSampleTypes(reader, reader.delimitedEnd());
      } else if (field === FRAME.DIFF_DETAILS && wireType === WireType.LENGTH_DELIMITED) {
        details.diffDetails = this.readDiffDetails(reader, reader.delimitedEnd());
      } else {
        reader.skip(wireType);
      }
    }
    return details;
  }

  private static readPosition(reader: ProtoWireReader, end: number): FramePosition {
    const position = new FramePosition(0, 0);
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (field === FRAME_POSITION.BCI && wireType === WireType.VARINT) {
        position.bci = reader.int32();
      } else if (field === FRAME_POSITION.LINE && wireType === WireType.VARINT) {
        position.line = reader.int32();
      } else {
        reader.skip(wireType);
      }
    }
    return position;
  }

  private static readSampleTypes(reader: ProtoWireReader, end: number): FrameSampleTypes {
    const types = new FrameSampleTypes(0, 0, 0, 0);
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (wireType !== WireType.VARINT) {
        reader.skip(wireType);
        continue;
      }
      switch (field) {
        case FRAME_SAMPLE_TYPES.INLINED:
          types.inlined = reader.int64();
          break;
        case FRAME_SAMPLE_TYPES.C1:
          types.c1 = reader.int64();
          break;
        case FRAME_SAMPLE_TYPES.INTERPRET:
          types.interpret = reader.int64();
          break;
        case FRAME_SAMPLE_TYPES.JIT:
          types.jit = reader.int64();
          break;
        default:
          reader.skip(wireType);
      }
    }
    return types;
  }

  private static readDiffDetails(reader: ProtoWireReader, end: number): DiffDetails {
    const diff = new DiffDetails(0, 0, 0, 0, 0, 0);
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (wireType === WireType.VARINT) {
        switch (field) {
          case DIFF_DETAILS.SAMPLES:
            diff.samples = reader.int64();
            continue;
          case DIFF_DETAILS.WEIGHT:
            diff.weight = reader.int64();
            continue;
          case DIFF_DETAILS.SECONDARY_SAMPLES:
            diff.secondarySamples = reader.int64();
            continue;
          case DIFF_DETAILS.SECONDARY_WEIGHT:
            diff.secondaryWeight = reader.int64();
            continue;
        }
      } else if (wireType === WireType.FIXED32) {
        switch (field) {
          case DIFF_DETAILS.PERCENT_SAMPLES:
            diff.percentSamples = reader.float();
            continue;
          case DIFF_DETAILS.PERCENT_WEIGHT:
            diff.percentWeight = reader.float();
            continue;
        }
      }
      reader.skip(wireType);
    }
    return diff;
  }

  private static readTimeseries(reader: ProtoWireReader, end: number): TimeseriesData {
    const series: Serie[] = [];
    while (reader.pos < end) {
      const tag = reader.uint32();
      const wireType = tag & WIRE_TYPE_MASK;
      if (
        tag >>> FIELD_NUMBER_SHIFT === TIMESERIES_DATA.SERIES &&
        wireType === WireType.LENGTH_DELIMITED
      ) {
        series.push(this.readSerie(reader, reader.delimitedEnd()));
      } else {
        reader.skip(wireType);
      }
    }
    return new TimeseriesData(series);
  }

  private static readSerie(reader: ProtoWireReader, end: number): Serie {
    let name = '';
    const points: number[][] = [];
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (field === TIMESERIES_SERIES.NAME && wireType === WireType.LENGTH_DELIMITED) {
        name = reader.string();
      } else if (field === TIMESERIES_SERIES.DATA && wireType === WireType.LENGTH_DELIMITED) {
        points.push(this.readPoint(reader, reader.delimitedEnd()));
      } else {
        reader.skip(wireType);
      }
    }
    return new Serie(points, name);
  }

  private static readPoint(reader: ProtoWireReader, end: number): number[] {
    let timestamp = 0;
    let value = 0;
    while (reader.pos < end) {
      const tag = reader.uint32();
      const field = tag >>> FIELD_NUMBER_SHIFT;
      const wireType = tag & WIRE_TYPE_MASK;
      if (field === TIMESERIES_POINT.TIMESTAMP && wireType === WireType.VARINT) {
        timestamp = reader.int64();
      } else if (field === TIMESERIES_POINT.VALUE && wireType === WireType.VARINT) {
        value = reader.int64();
      } else {
        reader.skip(wireType);
      }
    }
    return [timestamp, value];
  }
}
