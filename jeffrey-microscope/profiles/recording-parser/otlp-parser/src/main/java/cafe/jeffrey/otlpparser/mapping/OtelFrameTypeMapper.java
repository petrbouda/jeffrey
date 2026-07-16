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

import cafe.jeffrey.profile.common.model.FrameType;

import java.util.Locale;
import java.util.Map;

/**
 * Maps the OTLP semantic-convention attribute {@code profile.frame.type} onto Jeffrey's persisted
 * {@link FrameType} codes.
 * <p>
 * OTLP does not distinguish interpreted/JIT-compiled/inlined JVM frames, so {@code jvm} maps to
 * {@link FrameType#JIT_COMPILED} (the dominant state, rendered as a Java frame). Every non-JVM
 * language runtime folds into {@link FrameType#OTHER_RUNTIME} — a single dedicated color that makes
 * non-JVM runtime frames stand out in mixed-language stacks without diluting the palette.
 */
public final class OtelFrameTypeMapper {

    /**
     * Semantic-convention value marking a JVM frame ({@code profile.frame.type = "jvm"}).
     */
    public static final String FRAME_TYPE_JVM = "jvm";

    private static final String FRAME_TYPE_KERNEL = "kernel";
    private static final String FRAME_TYPE_NATIVE = "native";
    private static final String FRAME_TYPE_CPYTHON = "cpython";
    private static final String FRAME_TYPE_V8JS = "v8js";
    private static final String FRAME_TYPE_GO = "go";
    private static final String FRAME_TYPE_DOTNET = "dotnet";
    private static final String FRAME_TYPE_RUBY = "ruby";
    private static final String FRAME_TYPE_PHP = "php";
    private static final String FRAME_TYPE_PERL = "perl";
    private static final String FRAME_TYPE_BEAM = "beam";
    private static final String FRAME_TYPE_RUST = "rust";
    private static final String FRAME_TYPE_LUAJIT = "luajit";

    private static final Map<String, FrameType> FRAME_TYPES_BY_SEMCONV = Map.ofEntries(
            Map.entry(FRAME_TYPE_JVM, FrameType.JIT_COMPILED),
            Map.entry(FRAME_TYPE_KERNEL, FrameType.KERNEL),
            Map.entry(FRAME_TYPE_NATIVE, FrameType.NATIVE),
            Map.entry(FRAME_TYPE_CPYTHON, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_V8JS, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_GO, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_DOTNET, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_RUBY, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_PHP, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_PERL, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_BEAM, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_RUST, FrameType.OTHER_RUNTIME),
            Map.entry(FRAME_TYPE_LUAJIT, FrameType.OTHER_RUNTIME));

    private OtelFrameTypeMapper() {
    }

    public static FrameType map(String semconvFrameType) {
        if (semconvFrameType == null || semconvFrameType.isBlank()) {
            return FrameType.NATIVE;
        }
        FrameType mapped = FRAME_TYPES_BY_SEMCONV.get(semconvFrameType.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : FrameType.NATIVE;
    }

    public static boolean isJvmFrame(String semconvFrameType) {
        return FRAME_TYPE_JVM.equalsIgnoreCase(semconvFrameType);
    }
}
