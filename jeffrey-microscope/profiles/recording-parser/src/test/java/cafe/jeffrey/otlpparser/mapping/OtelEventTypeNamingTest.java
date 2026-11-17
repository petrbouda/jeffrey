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

import cafe.jeffrey.otlpparser.mapping.OtelEventTypeNaming.OtelEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OtelEventTypeNamingTest {

    @Test
    void codeAndLabelAreTheRawSampleTypeVerbatim() {
        assertEquals("cpu", OtelEventTypeNaming.resolve("cpu").name());
        assertEquals("alloc", OtelEventTypeNaming.resolve("alloc").name());
        assertEquals("alloc_space", OtelEventTypeNaming.resolve("alloc_space").name());

        OtelEventType resolved = OtelEventTypeNaming.resolve("alloc");
        assertEquals("alloc", resolved.label());
    }

    @Test
    void blankTypeFallsBackToSamples() {
        assertEquals("samples", OtelEventTypeNaming.resolve("").name());
        assertEquals("samples", OtelEventTypeNaming.resolve(null).name());
    }
}
