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

package cafe.jeffrey.pprofparser.mapping;

import com.google.perftools.profiles.ProfileProto.Function;
import com.google.perftools.profiles.ProfileProto.Line;
import com.google.perftools.profiles.ProfileProto.Location;
import com.google.perftools.profiles.ProfileProto.Mapping;
import cafe.jeffrey.pprofparser.PprofTables;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.shared.common.model.StacktraceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a pprof sample's leaf-first {@code location_id} list onto Jeffrey's root-first frame model.
 * <p>
 * pprof carries no frame-type information (no equivalent of OTLP's {@code profile.frame.type}), so
 * every frame is emitted as {@link FrameType#NATIVE} — these are the runtime's own application
 * frames, but the format does not tell us the language or compilation tier.
 */
public final class PprofFrameMapper {

    public record MappedStack(List<EventFrame> frames, StacktraceType type) {
    }

    private static final long UNKNOWN_BYTECODE_INDEX = -1;
    private static final String ADDRESS_PREFIX = "0x";
    private static final String UNKNOWN_MODULE = "unknown";
    private static final String FRAME_TYPE_CODE = FrameType.NATIVE.code();

    private PprofFrameMapper() {
    }

    public static MappedStack mapStack(List<Long> locationIds, PprofTables tables) {
        List<EventFrame> frames = new ArrayList<>();

        // location_ids are leaf-first; emit root-first by walking them in reverse
        for (int i = locationIds.size() - 1; i >= 0; i--) {
            Location location = tables.location(locationIds.get(i));
            if (location == null) {
                continue;
            }

            List<Line> lines = location.getLineList();
            if (lines.isEmpty()) {
                frames.add(addressFrame(location, tables));
                continue;
            }

            // line[last] is the caller, line[0] the innermost inlined callee — emit caller-first
            for (int lineIndex = lines.size() - 1; lineIndex >= 0; lineIndex--) {
                frames.add(lineFrame(lines.get(lineIndex), tables));
            }
        }

        StacktraceType type = frames.isEmpty() ? StacktraceType.UNKNOWN : StacktraceType.NATIVE;
        return new MappedStack(frames, type);
    }

    private static EventFrame lineFrame(Line line, PprofTables tables) {
        Function function = tables.function(line.getFunctionId());
        String functionName = resolveFunctionName(function, tables);
        if (functionName.isBlank()) {
            return new EventFrame(UNKNOWN_MODULE, "unknown", FRAME_TYPE_CODE, UNKNOWN_BYTECODE_INDEX, line.getLine());
        }
        FunctionNameSplitter.SplitName split = FunctionNameSplitter.split(functionName);
        return new EventFrame(split.clazz(), split.method(), FRAME_TYPE_CODE, UNKNOWN_BYTECODE_INDEX, line.getLine());
    }

    private static String resolveFunctionName(Function function, PprofTables tables) {
        if (function == null) {
            return "";
        }
        String name = tables.string(function.getName());
        if (!name.isBlank()) {
            return name;
        }
        return tables.string(function.getSystemName());
    }

    private static EventFrame addressFrame(Location location, PprofTables tables) {
        String method = ADDRESS_PREFIX + Long.toHexString(location.getAddress());
        String module = moduleName(location, tables);
        return new EventFrame(module, method, FRAME_TYPE_CODE, UNKNOWN_BYTECODE_INDEX, 0);
    }

    private static String moduleName(Location location, PprofTables tables) {
        Mapping mapping = tables.mapping(location.getMappingId());
        if (mapping == null) {
            return UNKNOWN_MODULE;
        }
        String filename = tables.string(mapping.getFilename());
        if (filename.isBlank()) {
            return UNKNOWN_MODULE;
        }
        int lastSlash = filename.lastIndexOf('/');
        return lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
    }
}
