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

import { describe, expect, it } from 'vitest';
import { cafe } from '@/proto/flamegraph';
import ProtobufConverter from './ProtobufConverter';
import FrameType from './FrameType';
import Frame from '@/services/api/model/Frame';
import FramePosition from '@/services/api/model/FramePosition';
import FrameSampleTypes from '@/services/api/model/FrameSampleTypes';
import DiffDetails from '@/services/api/model/DiffDetails';

/*
 * The decoder reads flamegraph.proto by hand. These tests hold it to the code generated from that
 * file (`npm run proto:generate`): what the generated code encodes, the decoder must read back field
 * for field, the way the protobufjs-based converter before it did.
 */

const proto = cafe.jeffrey.flamegraph.proto;
type IGraphData = cafe.jeffrey.flamegraph.proto.IGraphData;
type IFrame = cafe.jeffrey.flamegraph.proto.IFrame;

const FRAME_TYPE_PREFIX = 'FRAME_TYPE_';

function encodeBytes(graph: IGraphData): Uint8Array {
  return proto.GraphData.encode(proto.GraphData.create(graph)).finish();
}

function toBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

function encode(graph: IGraphData): ArrayBuffer {
  return toBuffer(encodeBytes(graph));
}

/** An int64 as protobufjs hands it over: a number, or a Long when the `long` package is present. */
function num(value: number | { toNumber(): number } | null | undefined): number {
  if (value == null) {
    return 0;
  }
  return typeof value === 'number' ? value : value.toNumber();
}

/** The frame the protobufjs-based converter made of a decoded frame message. */
function expectedFrame(frame: IFrame, titles: string[]): Frame {
  return new Frame(
    num(frame.leftSamples),
    num(frame.totalSamples),
    titles[frame.titleIndex ?? 0] ?? '',
    FrameType.fromProtoNumber(frame.type ?? 0),
    num(frame.leftWeight),
    num(frame.totalWeight),
    num(frame.selfSamples) || undefined,
    frame.position
      ? new FramePosition(frame.position.bci ?? 0, frame.position.line ?? 0)
      : undefined,
    frame.sampleTypes
      ? new FrameSampleTypes(
          num(frame.sampleTypes.inlined),
          num(frame.sampleTypes.c1),
          num(frame.sampleTypes.interpret),
          num(frame.sampleTypes.jit)
        )
      : undefined,
    frame.diffDetails
      ? new DiffDetails(
          num(frame.diffDetails.samples),
          num(frame.diffDetails.weight),
          frame.diffDetails.percentSamples ?? 0,
          frame.diffDetails.percentWeight ?? 0,
          num(frame.diffDetails.secondarySamples),
          num(frame.diffDetails.secondaryWeight)
        )
      : undefined,
    frame.prunedChildrenCount || undefined,
    frame.hidden || undefined
  );
}

const TITLES = [
  '1234 Event(s)',
  'org.example.OrderService#place',
  "Thread 'worker-1' ✓",
  'C:\\app\\native.dll'
];

/** Every field of every message, with the values that stress a varint reader. */
const FULL_GRAPH: IGraphData = {
  flamegraph: {
    depth: 3,
    titlePool: TITLES,
    levels: [
      { frames: [{ totalSamples: 1234, totalWeight: 8_878_517_000, titleIndex: 0 }] },
      {
        frames: [
          {
            leftSamples: 0,
            totalSamples: 1000,
            titleIndex: 1,
            type: proto.FrameType.FRAME_TYPE_JIT_COMPILED,
            leftWeight: 0,
            totalWeight: 2 ** 40,
            selfSamples: 7,
            position: { bci: 42, line: 340 },
            sampleTypes: { inlined: 1, c1: 2, interpret: 3, jit: 2 ** 33 },
            hidden: true
          },
          {
            leftSamples: 1000,
            totalSamples: 234,
            titleIndex: 2,
            type: proto.FrameType.FRAME_TYPE_THREAD_NAME_SYNTHETIC,
            leftWeight: 2 ** 40,
            totalWeight: 5,
            position: { bci: -1, line: 0 }
          }
        ]
      },
      {
        frames: [
          {
            leftSamples: 0,
            totalSamples: 600,
            titleIndex: 3,
            type: proto.FrameType.FRAME_TYPE_TRUNCATED_SYNTHETIC,
            prunedChildrenCount: 12,
            diffDetails: {
              samples: -250,
              weight: -(2 ** 41),
              percentSamples: -100,
              percentWeight: -12.5,
              secondarySamples: 250,
              secondaryWeight: 2 ** 41
            }
          },
          {
            leftSamples: 600,
            totalSamples: 400,
            titleIndex: 1,
            type: proto.FrameType.FRAME_TYPE_INLINED,
            diffDetails: {
              samples: 400,
              weight: 7,
              percentSamples: Infinity,
              percentWeight: Infinity
            }
          }
        ]
      }
    ]
  },
  timeseries: {
    series: [
      {
        name: 'samples',
        data: [
          { timestamp: 1_760_000_000_000, value: 5 },
          { timestamp: 1_760_000_001_000, value: 0 }
        ]
      },
      { name: '', data: [] }
    ]
  }
};

describe('ProtobufConverter', () => {
  describe('against the generated code', () => {
    it('reads every frame back as the protobufjs converter did', () => {
      const bytes = encodeBytes(FULL_GRAPH);
      const reference = proto.GraphData.decode(bytes).flamegraph!;

      const data = ProtobufConverter.decode(toBuffer(bytes)).requireFlamegraph();

      expect(data.depth).toBe(3);
      expect(data.titles).toEqual(TITLES);
      reference.levels!.forEach((level, levelIndex) => {
        expect(data.levels[levelIndex].length).toBe(level.frames!.length);
        level.frames!.forEach((frame, index) => {
          expect(data.frame(levelIndex, index)).toEqual(expectedFrame(frame, TITLES));
        });
      });
    });

    it('fills the columns the renderer draws from', () => {
      const data = ProtobufConverter.decode(encode(FULL_GRAPH)).requireFlamegraph();
      const diffLevel = data.levels[2];

      expect(Array.from(data.levels[1].totalWeight)).toEqual([2 ** 40, 5]);
      expect(Array.from(diffLevel.hasDiff)).toEqual([1, 1]);
      expect(Array.from(diffLevel.diffSamples)).toEqual([-250, 400]);
      expect(Array.from(diffLevel.diffWeight)).toEqual([-(2 ** 41), 7]);
      expect(Array.from(diffLevel.prunedChildren)).toEqual([12, 0]);
      expect(data.type(2, 0)).toBe(FrameType.TRUNCATED_SYNTHETIC);
    });

    it('reads the timeseries back point for point', () => {
      const timeseries = ProtobufConverter.decode(encode(FULL_GRAPH)).requireTimeseries();

      expect(timeseries.series.map(serie => serie.name)).toEqual(['samples', '']);
      expect(timeseries.series[0].data).toEqual([
        [1_760_000_000_000, 5],
        [1_760_000_001_000, 0]
      ]);
      expect(timeseries.series[1].data).toEqual([]);
    });

    it('names every frame type of the generated enum as the backend does', () => {
      const enumEntries = Object.entries(proto.FrameType).filter(
        (entry): entry is [string, number] => typeof entry[1] === 'number'
      );
      expect(enumEntries.length).toBeGreaterThan(0);
      for (const [name, number] of enumEntries) {
        expect(FrameType.fromProtoNumber(number), name).toBe(name.slice(FRAME_TYPE_PREFIX.length));
        expect(FrameType.toProtoNumber(name.slice(FRAME_TYPE_PREFIX.length)), name).toBe(number);
      }
    });
  });

  describe('a response', () => {
    const ROOT_ONLY = {
      depth: 1,
      titlePool: ['all'],
      levels: [{ frames: [{ totalSamples: 10, titleIndex: 0 }] }]
    };

    it('hands over the parts it carries and null for the others', () => {
      const data = ProtobufConverter.decode(encode({ flamegraph: ROOT_ONLY }));

      expect(data.requireFlamegraph().frame(0, 0).title).toBe('all');
      expect(data.timeseries).toBeNull();
      expect(() => data.requireTimeseries()).toThrow('no timeseries');
    });

    it('keeps an empty timeseries the server did send', () => {
      const data = ProtobufConverter.decode(encode({ timeseries: { series: [] } }));

      expect(data.requireTimeseries().series).toEqual([]);
      expect(() => data.requireFlamegraph()).toThrow('no flamegraph');
    });

    it('skips a field this client does not know', () => {
      // Field 15, varint 300, ahead of a regular graph: what a newer server could send
      const unknownField = Uint8Array.of((15 << 3) | 0, 0xac, 0x02);
      const graph = encodeBytes({ flamegraph: ROOT_ONLY });
      const bytes = new Uint8Array(unknownField.length + graph.length);
      bytes.set(unknownField);
      bytes.set(graph, unknownField.length);

      expect(ProtobufConverter.decode(toBuffer(bytes)).requireFlamegraph().title(0, 0)).toBe('all');
    });

    it('is rejected when it ends in the middle of a message', () => {
      const bytes = encodeBytes(FULL_GRAPH);

      expect(() => ProtobufConverter.decode(toBuffer(bytes.subarray(0, bytes.length - 7)))).toThrow(
        'Malformed protobuf'
      );
    });

    it('is rejected when its flamegraph has no root frame', () => {
      expect(() =>
        ProtobufConverter.decode(encode({ flamegraph: { depth: 0, levels: [] } }))
      ).toThrow('no root frame');
    });
  });
});
