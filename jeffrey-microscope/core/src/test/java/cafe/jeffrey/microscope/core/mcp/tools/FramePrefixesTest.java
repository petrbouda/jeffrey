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


package cafe.jeffrey.microscope.core.mcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramePrefixesTest {

    @Test
    void trimsAndDropsBlanksAndRepeats() {
        assertEquals(List.of("com.acme.", "org.shop."),
                FramePrefixes.of(Arrays.asList(" com.acme. ", null, "", "org.shop.", "com.acme.")));
        assertEquals(List.of(), FramePrefixes.of(null));
    }

    @Test
    void takesAsManyRootPackagesAsTheCapAndRefusesMore() {
        List<String> atCap = IntStream.range(0, FramePrefixes.MAX_PREFIXES).mapToObj(i -> "p" + i + ".").toList();
        assertEquals(atCap, FramePrefixes.of(atCap));

        List<String> past = IntStream.rangeClosed(0, FramePrefixes.MAX_PREFIXES).mapToObj(i -> "p" + i + ".").toList();
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FramePrefixes.of(past));
        assertTrue(refused.getMessage().contains(String.valueOf(FramePrefixes.MAX_PREFIXES)), refused.getMessage());
    }

    @Test
    void refusesAPrefixLongerThanAnyPackageName() {
        String tooLong = "a".repeat(FramePrefixes.MAX_PREFIX_CHARS + 1);
        assertThrows(IllegalArgumentException.class, () -> FramePrefixes.of(List.of(tooLong)));
    }
}
