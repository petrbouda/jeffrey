/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.frameir;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.common.model.profile.FrameType;
import pbouda.jeffrey.frameir.collector.FrameCollector;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameMergerTest {

    @Nested
    class StructureTest {

        @Test
        public void mergesEmptyFrames() {
            Frame actual = new FrameCollector<>(null).combiner(frame(null, "-"), frame(null, "-"));
            assertEquals(frame(null, "-"), actual);
        }

        @Test
        public void mergesRightIntoEmptyLeft() {
            Frame actual = new FrameCollector<>(null).combiner(frame(null, "-"), threeLayeredFrame());
            assertEquals(threeLayeredFrame(), actual);
        }

        @Test
        public void rightAddsNewFullStacktrace() {
            Frame dRoot = frame(null, "-");

            // --- 1st level
            Frame d = frame(dRoot, "d");
            dRoot.put("d", d);

            // --- 2nd level
            Frame dd = frame(d, "dd");
            d.put("dd", dd);

            // --- 3nd level
            dd.put("ddd", frame(dd, "ddd"));

            Frame expected = threeLayeredFrame();
            expected.put("d", d);

            Frame actual = new FrameCollector<>(null).combiner(threeLayeredFrame(), dRoot);
            assertEquals(expected, actual);
        }

        @Test
        public void rightAddsPartialStacktrace() {
            Frame root = frame(null, "-");

            // --- 1st level
            Frame b = frame(root, "b");
            root.put("b", b);

            // --- 2nd level
            Frame bb = frame(b, "bb");
            b.put("bb", bb);

            // --- 3nd level
            bb.put("bbb", frame(bb, "bbb"));

            Frame expected = threeLayeredFrame();
            expected.put("b", b);

            Frame actual = new FrameCollector<>(null).combiner(threeLayeredFrame(), root);
            assertEquals(expected, actual);
        }
    }

    @Nested
    class FrameValuesTest {

        @Test
        public void mergesFrameValues() {
            Frame left = frame(null, "-");
            left.increment(FrameType.C1_COMPILED, 1, 1, true);
            left.increment(FrameType.NATIVE, 2, 2, false);

            Frame right = frame(null, "-");
            right.increment(FrameType.C1_COMPILED, 3, 3, true);
            right.increment(FrameType.NATIVE, 4, 4, false);

            Frame expected = frame(null, "-");
            expected.increment(FrameType.C1_COMPILED, 4, 4, true);
            expected.increment(FrameType.NATIVE, 6, 6, false);

            Frame actual = new FrameCollector<>(null).combiner(left, right);
            assertEquals(expected, actual);
        }

        @Test
        public void mergesFrameValuesWithDifferentTypes() {
            Frame left = frame(null, "-");
            left.increment(FrameType.C1_COMPILED, 1, 1, true);
            left.increment(FrameType.NATIVE, 2, 2, false);

            Frame right = frame(null, "-");
            right.increment(FrameType.CPP, 3, 3, true);
            right.increment(FrameType.INTERPRETED, 4, 4, false);

            Frame expected = frame(null, "-");
            expected.increment(FrameType.C1_COMPILED, 1, 1, true);
            expected.increment(FrameType.NATIVE, 2, 2, false);
            expected.increment(FrameType.CPP, 3, 3, true);
            expected.increment(FrameType.INTERPRETED, 4, 4, false);

            Frame actual = new FrameCollector<>(null).combiner(left, right);
            assertEquals(expected, actual);
        }

        @Test
        public void mergingInsideTheStacktrace() {
            //
            // ROOT 1
            //

            Frame root = frame(null, "-");

            // --- 1st level
            Frame a = frame(root, "a");
            a.increment(FrameType.C1_COMPILED, 1, 1, false);
            a.increment(FrameType.INTERPRETED, 1, 1, false);
            a.increment(FrameType.INLINED, 1, 1, false);
            root.put("a", a);

            // --- 2nd level
            Frame aa = frame(a, "aa");
            aa.increment(FrameType.C1_COMPILED, 1, 1, false);
            aa.increment(FrameType.INTERPRETED, 1, 1, false);
            aa.increment(FrameType.INLINED, 1, 1, false);
            a.put("aa", aa);

            // --- 3rd level
            Frame aaa = frame(aa, "aaa");
            aaa.increment(FrameType.C1_COMPILED, 1, 1, false);
            aaa.increment(FrameType.INTERPRETED, 1, 1, false);
            aaa.increment(FrameType.INLINED, 1, 1, false);
            aa.put("aaa", aaa);

            //
            // ROOT 2
            //

            Frame root2 = frame(null, "-");

            // --- 1st level
            Frame a2 = frame(root2, "a");
            a2.increment(FrameType.C1_COMPILED, 0, 0, false);
            a2.increment(FrameType.INTERPRETED, 0, 0, false);
            a2.increment(FrameType.INLINED, 0, 0, false);
            root2.put("a", a2);

            // --- 2nd level
            Frame aa2 = frame(a2, "aa");
            aa2.increment(FrameType.C1_COMPILED, 1, 1, false);
            aa2.increment(FrameType.INTERPRETED, 1, 1, false);
            // different one
            aa2.increment(FrameType.KERNEL, 1, 1, false);
            a2.put("aa", aa2);

            //
            // EXPECTED
            //

            Frame expected = frame(null, "-");

            // --- 1st level
            Frame ae = frame(expected, "a");
            ae.increment(FrameType.C1_COMPILED, 1, 1, false);
            ae.increment(FrameType.INTERPRETED, 1, 1, false);
            ae.increment(FrameType.INLINED, 1, 1, false);
            expected.put("a", ae);

            // --- 2nd level
            Frame aae = frame(ae, "aa");
            aae.increment(FrameType.C1_COMPILED, 2, 2, false);
            aae.increment(FrameType.INTERPRETED, 2, 2, false);
            aae.increment(FrameType.INLINED, 1, 1, false);
            aae.increment(FrameType.KERNEL, 1, 1, false);
            ae.put("aa", aae);

            // --- 3rd level
            Frame aaae = frame(aae, "aaa");
            aaae.increment(FrameType.C1_COMPILED, 1, 1, false);
            aaae.increment(FrameType.INTERPRETED, 1, 1, false);
            aaae.increment(FrameType.INLINED, 1, 1, false);
            aae.put("aaa", aaae);


            Frame actual = new FrameCollector<>(null).combiner(root, root2);
            assertEquals(expected, actual);
        }
    }

    private static Frame frame(Frame parent, String methodName) {
        return new Frame(parent, methodName, 1, 1);
    }

    private static Frame threeLayeredFrame() {
        Frame root = frame(null, "-");

        // --- 1st level
        Frame a = frame(root, "a");
        root.put("a", a);

        Frame b = frame(root, "b");
        root.put("b", b);

        Frame c = frame(root, "c");
        root.put("c", c);

        // --- 2nd level
        Frame aa = frame(a, "aa");
        a.put("aa", aa);

        Frame ca = frame(c, "ca");
        c.put("ca", ca);

        // --- 3rd level
        aa.put("aaa", frame(aa, "aaa"));

        return root;
    }
}
