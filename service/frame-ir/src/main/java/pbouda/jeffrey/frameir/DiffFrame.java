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

package pbouda.jeffrey.frameir;

import pbouda.jeffrey.common.model.profile.FrameType;

import java.util.TreeMap;

public class DiffFrame extends TreeMap<String, DiffFrame> {

    public enum Type {
        REMOVED, ADDED, SHARED
    }

    public final Type type;
    public final Frame frame;
    public final String methodName;
    public final FrameType frameType;
    public final long secondarySamples;
    public final long secondaryWeight;
    public final long primarySamples;
    public final long primaryWeight;

    public DiffFrame(
            Type type,
            Frame frame,
            String methodName,
            FrameType frameType
    ) {
        this(type, frame, methodName, frameType, -1, -1, -1, -1);
    }

    public DiffFrame(
            Type type,
            Frame frame,
            String methodName,
            FrameType frameType,
            long primarySamples,
            long primaryWeight,
            long secondarySamples,
            long secondaryWeight
    ) {
        this.type = type;
        this.frame = frame;
        this.methodName = methodName;
        this.frameType = frameType;
        this.secondarySamples = secondarySamples;
        this.secondaryWeight = secondaryWeight;
        this.primarySamples = primarySamples;
        this.primaryWeight = primaryWeight;
    }

    public static DiffFrame removed(Frame frame, String methodName) {
        return new DiffFrame(Type.REMOVED, frame, methodName, FrameType.UNKNOWN);
    }

    public static DiffFrame added(Frame frame, String methodName) {
        return new DiffFrame(Type.ADDED, frame, methodName, FrameType.UNKNOWN);
    }

    public static DiffFrame shared(
            String methodName,
            FrameType frameType,
            long primarySamples,
            long primaryWeight,
            long secondarySamples,
            long secondaryWeight
    ) {
        return new DiffFrame(
                Type.SHARED,
                null,
                methodName,
                frameType,
                primarySamples,
                primaryWeight,
                secondarySamples,
                secondaryWeight
        );
    }

    public long samples() {
        return switch (type) {
            case REMOVED, ADDED -> frame.totalSamples();
            case SHARED -> secondarySamples + primarySamples;
        };
    }

    public long weight() {
        return switch (type) {
            case REMOVED, ADDED -> frame.totalWeight();
            case SHARED -> secondaryWeight + primaryWeight;
        };
    }
}
