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

import { pbouda } from '@/proto/flamegraph';
import BothGraphData from '@/services/flamegraphs/model/BothGraphData';
import FlamegraphData from '@/services/flamegraphs/model/FlamegraphData';
import Frame from '@/services/flamegraphs/model/Frame';
import FramePosition from '@/services/flamegraphs/model/FramePosition';
import FrameSampleTypes from '@/services/flamegraphs/model/FrameSampleTypes';
import DiffDetails from '@/services/flamegraphs/model/DiffDetails';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import Serie from '@/services/timeseries/model/Serie';

type ProtoGraphData = pbouda.jeffrey.flamegraph.proto.GraphData;
type ProtoFlamegraphData = pbouda.jeffrey.flamegraph.proto.IFlamegraphData;
type ProtoFrame = pbouda.jeffrey.flamegraph.proto.IFrame;
type ProtoTimeseriesData = pbouda.jeffrey.flamegraph.proto.ITimeseriesData;
type ProtoFrameType = pbouda.jeffrey.flamegraph.proto.FrameType;

const FrameTypeEnum = pbouda.jeffrey.flamegraph.proto.FrameType;

/**
 * Maps protobuf FrameType enum to string representation used in frontend.
 */
const FRAME_TYPE_MAP: Record<number, string> = {
    [FrameTypeEnum.FRAME_TYPE_UNKNOWN]: 'UNKNOWN',
    [FrameTypeEnum.FRAME_TYPE_C1_COMPILED]: 'C1_COMPILED',
    [FrameTypeEnum.FRAME_TYPE_NATIVE]: 'NATIVE',
    [FrameTypeEnum.FRAME_TYPE_CPP]: 'CPP',
    [FrameTypeEnum.FRAME_TYPE_INTERPRETED]: 'INTERPRETED',
    [FrameTypeEnum.FRAME_TYPE_JIT_COMPILED]: 'JIT_COMPILED',
    [FrameTypeEnum.FRAME_TYPE_INLINED]: 'INLINED',
    [FrameTypeEnum.FRAME_TYPE_KERNEL]: 'KERNEL',
    [FrameTypeEnum.FRAME_TYPE_THREAD_NAME_SYNTHETIC]: 'THREAD_NAME_SYNTHETIC',
    [FrameTypeEnum.FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC]: 'ALLOCATED_OBJECT_SYNTHETIC',
    [FrameTypeEnum.FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC]: 'ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC',
    [FrameTypeEnum.FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC]: 'ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC',
    [FrameTypeEnum.FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC]: 'BLOCKING_OBJECT_SYNTHETIC',
    [FrameTypeEnum.FRAME_TYPE_LAMBDA_SYNTHETIC]: 'LAMBDA_SYNTHETIC',
    [FrameTypeEnum.FRAME_TYPE_HIGHLIGHTED_WARNING]: 'HIGHLIGHTED_WARNING',
};

/**
 * Converts Protocol Buffers data to internal frontend types.
 * Handles title deduplication by resolving title indices from the title pool.
 */
export default class ProtobufConverter {
    /**
     * Decodes binary protobuf data and converts to BothGraphData.
     */
    static decode(data: ArrayBuffer): BothGraphData {
        const protoData = pbouda.jeffrey.flamegraph.proto.GraphData.decode(new Uint8Array(data));
        return this.convert(protoData);
    }

    /**
     * Converts decoded protobuf GraphData to BothGraphData.
     */
    static convert(protoData: ProtoGraphData): BothGraphData {
        const flamegraph = protoData.flamegraph ? this.convertFlamegraph(protoData.flamegraph) : null;
        const timeseries = protoData.timeseries ? this.convertTimeseries(protoData.timeseries) : null;
        return new BothGraphData(flamegraph!, timeseries!);
    }

    private static convertFlamegraph(proto: ProtoFlamegraphData): FlamegraphData {
        const titlePool = proto.titlePool || [];
        const levels: Frame[][] = [];

        for (const protoLevel of proto.levels || []) {
            const frames: Frame[] = [];
            for (const protoFrame of protoLevel.frames || []) {
                frames.push(this.convertFrame(protoFrame, titlePool));
            }
            levels.push(frames);
        }

        return new FlamegraphData(proto.depth || 0, levels);
    }

    private static convertFrame(proto: ProtoFrame, titlePool: string[]): Frame {
        // Resolve title from pool using titleIndex
        const title = titlePool[proto.titleIndex || 0] || '';

        // Map frame type enum to string
        const typeString = FRAME_TYPE_MAP[proto.type || 0] || 'UNKNOWN';

        // Convert position if present
        let position: FramePosition | undefined;
        if (proto.position) {
            position = new FramePosition(
                proto.position.bci || 0,
                proto.position.line || 0
            );
        }

        // Convert sample types if present
        let sampleTypes: FrameSampleTypes | undefined;
        if (proto.sampleTypes) {
            sampleTypes = new FrameSampleTypes(
                this.toLong(proto.sampleTypes.inlined),
                this.toLong(proto.sampleTypes.c1),
                this.toLong(proto.sampleTypes.interpret),
                this.toLong(proto.sampleTypes.jit)
            );
        }

        // Convert diff details if present
        let diffDetails: DiffDetails | undefined;
        if (proto.diffDetails) {
            diffDetails = new DiffDetails(
                this.toLong(proto.diffDetails.samples),
                this.toLong(proto.diffDetails.weight),
                proto.diffDetails.percentSamples || 0,
                proto.diffDetails.percentWeight || 0
            );
        }

        return new Frame(
            this.toLong(proto.leftSamples),
            this.toLong(proto.totalSamples),
            title,
            typeString,
            proto.leftWeight ? this.toLong(proto.leftWeight) : undefined,
            proto.totalWeight ? this.toLong(proto.totalWeight) : undefined,
            proto.selfSamples ? this.toLong(proto.selfSamples) : undefined,
            position,
            sampleTypes,
            diffDetails,
            proto.beforeMarker || undefined
        );
    }

    private static convertTimeseries(proto: ProtoTimeseriesData): TimeseriesData {
        const series: Serie[] = [];

        for (const protoSeries of proto.series || []) {
            const data: number[][] = [];
            for (const point of protoSeries.data || []) {
                data.push([
                    this.toLong(point.timestamp),
                    this.toLong(point.value)
                ]);
            }
            series.push(new Serie(data, protoSeries.name || ''));
        }

        return new TimeseriesData(series);
    }

    /**
     * Converts Long or number to number.
     * Protobuf int64 values can be Long objects when using protobufjs.
     */
    private static toLong(value: number | Long | null | undefined): number {
        if (value == null) return 0;
        if (typeof value === 'number') return value;
        // Long object from protobufjs
        return (value as any).toNumber?.() ?? 0;
    }
}
