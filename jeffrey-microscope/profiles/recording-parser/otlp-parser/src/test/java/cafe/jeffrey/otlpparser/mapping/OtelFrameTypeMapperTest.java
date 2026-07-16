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
    void commonJvmNeighborsMapToDedicatedFrameTypes() {
        assertEquals(FrameType.NATIVE, OtelFrameTypeMapper.map("native"));
        assertEquals(FrameType.PYTHON, OtelFrameTypeMapper.map("cpython"));
        assertEquals(FrameType.JAVASCRIPT, OtelFrameTypeMapper.map("v8js"));
        assertEquals(FrameType.GO, OtelFrameTypeMapper.map("go"));
    }

    @Test
    void rareRuntimesFoldIntoOtherRuntime() {
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("dotnet"));
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("ruby"));
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("php"));
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("perl"));
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("beam"));
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("rust"));
        assertEquals(FrameType.OTHER_RUNTIME, OtelFrameTypeMapper.map("luajit"));
    }

    @Test
    void missingOrUnknownValuesMapToNative() {
        assertEquals(FrameType.NATIVE, OtelFrameTypeMapper.map(null));
        assertEquals(FrameType.NATIVE, OtelFrameTypeMapper.map(""));
        assertEquals(FrameType.NATIVE, OtelFrameTypeMapper.map("some-future-runtime"));
        assertFalse(OtelFrameTypeMapper.isJvmFrame(null));
    }

    @Test
    void mappedCodesRoundTripThroughFrameType() {
        assertEquals(FrameType.JIT_COMPILED, FrameType.fromCode(OtelFrameTypeMapper.map("jvm").code()));
        assertEquals(FrameType.KERNEL, FrameType.fromCode(OtelFrameTypeMapper.map("kernel").code()));
        assertEquals(FrameType.NATIVE, FrameType.fromCode(OtelFrameTypeMapper.map("native").code()));
        assertEquals(FrameType.PYTHON, FrameType.fromCode(OtelFrameTypeMapper.map("cpython").code()));
        assertEquals(FrameType.GO, FrameType.fromCode(OtelFrameTypeMapper.map("go").code()));
        assertEquals(FrameType.OTHER_RUNTIME, FrameType.fromCode(OtelFrameTypeMapper.map("luajit").code()));
    }
}
