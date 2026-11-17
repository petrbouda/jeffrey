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
