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
 * {@link FrameType#JIT_COMPILED} (the dominant state, rendered as a Java frame). Genuine native
 * ({@code native}) and {@code kernel} frames keep their own types, but every non-JVM language
 * runtime ({@code cpython}, {@code go}, {@code v8js}, ...) maps to {@link FrameType#UNKNOWN} — a
 * neutral fill — rather than {@link FrameType#NATIVE}, whose red rendering would wrongly flag
 * legitimate application code as native. Blank/unrecognized frame types default to
 * {@link FrameType#UNKNOWN} for the same reason. Language-specific frame types are a future
 * enhancement.
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

    // jvm/native/kernel keep their own Jeffrey frame types. Every non-JVM language runtime maps to
    // UNKNOWN (a neutral fill) instead of NATIVE, so legitimate application code is not painted red.
    private static final Map<String, FrameType> FRAME_TYPES_BY_SEMCONV = Map.ofEntries(
            Map.entry(FRAME_TYPE_JVM, FrameType.JIT_COMPILED),
            Map.entry(FRAME_TYPE_KERNEL, FrameType.KERNEL),
            Map.entry(FRAME_TYPE_NATIVE, FrameType.NATIVE),
            Map.entry(FRAME_TYPE_CPYTHON, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_V8JS, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_GO, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_DOTNET, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_RUBY, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_PHP, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_PERL, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_BEAM, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_RUST, FrameType.UNKNOWN),
            Map.entry(FRAME_TYPE_LUAJIT, FrameType.UNKNOWN));

    private OtelFrameTypeMapper() {
    }

    public static FrameType map(String semconvFrameType) {
        if (semconvFrameType == null || semconvFrameType.isBlank()) {
            return FrameType.UNKNOWN;
        }
        FrameType mapped = FRAME_TYPES_BY_SEMCONV.get(semconvFrameType.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : FrameType.UNKNOWN;
    }

    public static boolean isJvmFrame(String semconvFrameType) {
        return FRAME_TYPE_JVM.equalsIgnoreCase(semconvFrameType);
    }

    /**
     * Inverse of {@link #map(String)} for export: derives the OTLP {@code profile.frame.type}
     * semantic-convention value from a Jeffrey {@link FrameType}. Java frames (interpreted / JIT /
     * inlined / C1) collapse to {@code jvm}, {@link FrameType#KERNEL} to {@code kernel}, and
     * {@link FrameType#NATIVE}/{@link FrameType#CPP} to {@code native}. Any other type (notably
     * {@link FrameType#UNKNOWN} and synthetic frames) returns {@code null} — no attribute is written,
     * so a re-import maps it back to {@link FrameType#UNKNOWN}.
     */
    public static String toSemconv(FrameType frameType) {
        if (frameType == null) {
            return null;
        }
        if (frameType.isJavaFrame()) {
            return FRAME_TYPE_JVM;
        }
        return switch (frameType) {
            case KERNEL -> FRAME_TYPE_KERNEL;
            case NATIVE, CPP -> FRAME_TYPE_NATIVE;
            default -> null;
        };
    }
}
