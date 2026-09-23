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

package cafe.jeffrey.frameir;

import cafe.jeffrey.profile.common.model.FrameType;

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
