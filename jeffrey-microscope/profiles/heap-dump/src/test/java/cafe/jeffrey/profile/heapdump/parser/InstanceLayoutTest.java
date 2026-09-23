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
package cafe.jeffrey.profile.heapdump.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-record coverage of {@link InstanceLayout}'s three branches and the
 * alignment / oop-overhead helpers. The compressed-oops branch is already
 * exercised end-to-end in {@link HprofIndexShallowSizeTest}; this test
 * complements that with the 32-bit and uncompressed-oops branches that are
 * impractical to provoke through {@code HprofIndex.build} (a 32 GiB synthetic
 * dump would be needed for the uncompressed path).
 */
class InstanceLayoutTest {

    @Nested
    class Branches {

        @Test
        void thirtyTwoBitLayout() {
            InstanceLayout l = InstanceLayout.from(4, /* compressedOops */ false);
            assertEquals(8, l.objectHeader());
            assertEquals(12, l.arrayHeader());
            assertEquals(4, l.idSize());
            assertEquals(4, l.oopSize());
            assertEquals(8, l.objectAlignment());
        }

        @Test
        void thirtyTwoBitIgnoresCompressedOopsArgument() {
            // On a 32-bit JVM the compressed-oops flag is meaningless — the
            // factory must return the 32-bit shape regardless.
            InstanceLayout flagOn = InstanceLayout.from(4, true);
            InstanceLayout flagOff = InstanceLayout.from(4, false);
            assertEquals(flagOff.objectHeader(), flagOn.objectHeader());
            assertEquals(flagOff.arrayHeader(), flagOn.arrayHeader());
            assertEquals(flagOff.oopSize(), flagOn.oopSize());
        }

        @Test
        void sixtyFourBitCompressedOopsLayout() {
            InstanceLayout l = InstanceLayout.from(8, true);
            assertEquals(16, l.objectHeader());
            assertEquals(16, l.arrayHeader());
            assertEquals(8, l.idSize());
            assertEquals(4, l.oopSize());
            assertEquals(8, l.objectAlignment());
        }

        @Test
        void sixtyFourBitUncompressedOopsLayout() {
            InstanceLayout l = InstanceLayout.from(8, false);
            assertEquals(16, l.objectHeader());
            assertEquals(24, l.arrayHeader());
            assertEquals(8, l.idSize());
            assertEquals(8, l.oopSize());
            assertEquals(8, l.objectAlignment());
        }
    }

    @Nested
    class OopOverhead {

        @Test
        void zeroOnUncompressed64Bit() {
            assertEquals(0, InstanceLayout.from(8, false).oopOverheadDelta());
        }

        @Test
        void fourOnCompressed64Bit() {
            // On-disk OOPs are 8 bytes; on-heap are 4. Each reference over-counts
            // by 4 in the raw HPROF shallow size and must be deducted.
            assertEquals(4, InstanceLayout.from(8, true).oopOverheadDelta());
        }

        @Test
        void zeroOn32Bit() {
            assertEquals(0, InstanceLayout.from(4, false).oopOverheadDelta());
        }
    }

    @Nested
    class AlignUp {

        @Test
        void alreadyAlignedReturnedUnchanged() {
            InstanceLayout l = InstanceLayout.from(8, true);
            assertEquals(16, l.alignUp(16));
            assertEquals(24, l.alignUp(24));
            assertEquals(0, l.alignUp(0));
        }

        @Test
        void unalignedRoundedUpToNextBoundary() {
            InstanceLayout l = InstanceLayout.from(8, true);
            assertEquals(8, l.alignUp(1));
            assertEquals(8, l.alignUp(7));
            assertEquals(16, l.alignUp(9));
            assertEquals(24, l.alignUp(17));
        }
    }
}
