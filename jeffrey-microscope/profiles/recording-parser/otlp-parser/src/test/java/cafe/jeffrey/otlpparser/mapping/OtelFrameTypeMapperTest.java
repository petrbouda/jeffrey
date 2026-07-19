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

import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.model.FrameType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelFrameTypeMapperTest {

    @Test
    void jvmMapsToJitCompiled() {
        assertEquals(FrameType.JIT_COMPILED, OtelFrameTypeMapper.map("jvm"));
        assertTrue(OtelFrameTypeMapper.isJvmFrame("jvm"));
    }

    @Test
    void kernelMapsToKernel() {
        assertEquals(FrameType.KERNEL, OtelFrameTypeMapper.map("kernel"));
    }

    @Test
    void genuineNativeMapsToNative() {
        assertEquals(FrameType.NATIVE, OtelFrameTypeMapper.map("native"));
    }

    @Test
    void nonJvmLanguageRuntimesMapToUnknown() {
        // Language-execution frames must not render as red NATIVE — they map to the neutral UNKNOWN fill.
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map("cpython"));
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map("go"));
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map("v8js"));
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map("dotnet"));
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map("ruby"));
    }

    @Test
    void missingOrUnknownValuesMapToUnknown() {
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map(null));
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map(""));
        assertEquals(FrameType.UNKNOWN, OtelFrameTypeMapper.map("some-future-runtime"));
        assertFalse(OtelFrameTypeMapper.isJvmFrame(null));
    }

    @Test
    void mappedCodesRoundTripThroughFrameType() {
        assertEquals(FrameType.JIT_COMPILED, FrameType.fromCode(OtelFrameTypeMapper.map("jvm").code()));
        assertEquals(FrameType.KERNEL, FrameType.fromCode(OtelFrameTypeMapper.map("kernel").code()));
        assertEquals(FrameType.NATIVE, FrameType.fromCode(OtelFrameTypeMapper.map("native").code()));
    }
}
