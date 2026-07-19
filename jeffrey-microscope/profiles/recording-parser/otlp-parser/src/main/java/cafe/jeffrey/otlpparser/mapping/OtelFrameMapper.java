/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.otlpparser.mapping;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.profiles.v1development.Line;
import io.opentelemetry.proto.profiles.v1development.Location;
import io.opentelemetry.proto.profiles.v1development.Mapping;
import io.opentelemetry.proto.profiles.v1development.Stack;
import io.opentelemetry.proto.profiles.v1development.Function;
import cafe.jeffrey.otlpparser.dictionary.OtlpDictionary;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.shared.common.model.StacktraceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps an OTLP {@code Stack} (leaf-first list of locations) onto Jeffrey's frame model
 * (root-first list of {@link EventFrame}s).
 * <ul>
 *   <li>Location order is reversed (OTLP stacks are leaf-first, Jeffrey persists root-first).</li>
 *   <li>A location with multiple {@code Line}s is inline-expanded: the last line is the caller
 *       (the actual compiled frame), the preceding lines are the inlined callees — emitted
 *       caller-first with {@link FrameType#INLINED} for JVM callees.</li>
 *   <li>JVM function names are split into class/method; native frames use the mapping's file
 *       basename as the "class" (module), matching the async-profiler reading of native frames.</li>
 *   <li>Symbol-less locations render as a hexadecimal address frame.</li>
 * </ul>
 */
public final class OtelFrameMapper {

    /**
     * A mapped stacktrace: root-first frames plus the resolved stacktrace classification.
     */
    public record MappedStack(List<EventFrame> frames, StacktraceType type) {
    }

    private static final long UNKNOWN_BYTECODE_INDEX = -1;
    private static final String ADDRESS_PREFIX = "0x";
    private static final String UNKNOWN_MODULE = "unknown";

    private OtelFrameMapper() {
    }

    public static MappedStack mapStack(Stack stack, OtlpDictionary dictionary) {
        List<EventFrame> frames = new ArrayList<>();
        boolean anyJavaFrame = false;

        List<Integer> locationIndices = stack.getLocationIndicesList();
        for (int i = locationIndices.size() - 1; i >= 0; i--) {
            Location location = dictionary.location(locationIndices.get(i));
            if (location == null) {
                continue;
            }

            FrameType frameType = resolveFrameType(location, dictionary);
            boolean jvmFrame = frameType.isJavaFrame();
            anyJavaFrame |= jvmFrame;

            String module = resolveModule(location, dictionary);
            List<Line> lines = location.getLinesList();
            if (lines.isEmpty()) {
                frames.add(addressFrame(location, module, frameType));
                continue;
            }

            // The last line is the caller (the real compiled frame); the preceding lines were
            // inlined into it. Root-first output emits the caller before its inlined callees.
            for (int lineIndex = lines.size() - 1; lineIndex >= 0; lineIndex--) {
                boolean inlined = lineIndex < lines.size() - 1;
                FrameType lineFrameType = (inlined && jvmFrame) ? FrameType.INLINED : frameType;
                frames.add(lineFrame(lines.get(lineIndex), location, module, lineFrameType, jvmFrame, dictionary));
            }
        }

        return new MappedStack(frames, resolveStacktraceType(frames, anyJavaFrame));
    }

    private static StacktraceType resolveStacktraceType(List<EventFrame> frames, boolean anyJavaFrame) {
        if (frames.isEmpty()) {
            return StacktraceType.UNKNOWN;
        }
        return anyJavaFrame ? StacktraceType.APPLICATION : StacktraceType.NATIVE;
    }

    private static FrameType resolveFrameType(Location location, OtlpDictionary dictionary) {
        Map<String, AnyValue> attributes = OtlpAttributes.resolve(location.getAttributeIndicesList(), dictionary);
        String semconvFrameType = OtlpAttributes.stringValue(attributes.get(OtelSemconv.PROFILE_FRAME_TYPE));
        return OtelFrameTypeMapper.map(semconvFrameType);
    }

    private static String resolveModule(Location location, OtlpDictionary dictionary) {
        Mapping mapping = dictionary.mapping(location.getMappingIndex());
        if (mapping == null) {
            return "";
        }
        String filename = dictionary.string(mapping.getFilenameStrindex());
        if (filename.isBlank()) {
            return "";
        }
        int lastSlash = filename.lastIndexOf('/');
        return lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
    }

    private static EventFrame lineFrame(
            Line line,
            Location location,
            String module,
            FrameType frameType,
            boolean jvmFrame,
            OtlpDictionary dictionary) {

        String functionName = resolveFunctionName(line, dictionary);
        if (functionName.isBlank()) {
            return addressFrame(location, module, frameType);
        }

        if (jvmFrame) {
            FunctionNameSplitter.SplitName splitName = FunctionNameSplitter.split(functionName);
            return new EventFrame(
                    splitName.clazz(),
                    splitName.method(),
                    frameType.code(),
                    UNKNOWN_BYTECODE_INDEX,
                    line.getLine());
        }
        return new EventFrame(
                module,
                functionName,
                frameType.code(),
                UNKNOWN_BYTECODE_INDEX,
                line.getLine());
    }

    private static String resolveFunctionName(Line line, OtlpDictionary dictionary) {
        Function function = dictionary.function(line.getFunctionIndex());
        if (function == null) {
            return "";
        }
        String name = dictionary.string(function.getNameStrindex());
        if (!name.isBlank()) {
            return name;
        }
        return dictionary.string(function.getSystemNameStrindex());
    }

    private static EventFrame addressFrame(Location location, String module, FrameType frameType) {
        String method = ADDRESS_PREFIX + Long.toHexString(location.getAddress());
        String clazz = module.isBlank() ? UNKNOWN_MODULE : module;
        return new EventFrame(clazz, method, frameType.code(), UNKNOWN_BYTECODE_INDEX, 0);
    }
}
