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

package cafe.jeffrey.frameir;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.model.FrameType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Language-runtime frames (mixed-language stacks from whole-system profilers) share a single
 * counter + last-seen type instead of per-language counter fields — these tests pin the dominant
 * type resolution and merging semantics of that mechanism.
 */
class FrameLanguageTypeTest {

    private static Frame frame() {
        return new Frame(null, "sample_function", 0, 0);
    }

    @Nested
    class DominantType {

        @Test
        void languageOnlyFrameResolvesToItsLanguage() {
            Frame frame = frame();
            frame.increment(FrameType.PYTHON, 10, 10, true);

            assertEquals(FrameType.PYTHON, frame.frameType());
        }

        @Test
        void everyLanguageRuntimeIsResolvable() {
            for (FrameType language : new FrameType[]{
                    FrameType.PYTHON, FrameType.JAVASCRIPT, FrameType.GO, FrameType.DOTNET,
                    FrameType.RUBY, FrameType.PHP, FrameType.PERL, FrameType.BEAM,
                    FrameType.RUST, FrameType.LUA}) {
                Frame frame = frame();
                frame.increment(language, 1, 1, true);
                assertEquals(language, frame.frameType(), "language=" + language);
            }
        }

        @Test
        void languageWinsOverNativeSamples() {
            Frame frame = frame();
            frame.increment(FrameType.GO, 5, 5, true);
            frame.increment(FrameType.NATIVE, 1, 1, true);

            assertEquals(FrameType.GO, frame.frameType());
        }

        @Test
        void kernelStillWinsOverLanguageSamples() {
            Frame frame = frame();
            frame.increment(FrameType.RUBY, 5, 5, true);
            frame.increment(FrameType.KERNEL, 1, 1, true);

            assertEquals(FrameType.KERNEL, frame.frameType());
        }
    }

    @Nested
    class Merging {

        @Test
        void mergePropagatesLanguageTypeAndSamples() {
            Frame target = frame();
            target.increment(FrameType.NATIVE, 1, 1, true);

            Frame source = frame();
            source.increment(FrameType.PYTHON, 7, 7, true);

            target.merge(source);

            assertEquals(FrameType.PYTHON, target.frameType());
            assertEquals(8, target.totalSamples());
        }

        @Test
        void mergeWithoutLanguageKeepsExistingLanguage() {
            Frame target = frame();
            target.increment(FrameType.LUA, 3, 3, true);

            Frame source = frame();
            source.increment(FrameType.NATIVE, 1, 1, true);

            target.merge(source);

            assertEquals(FrameType.LUA, target.frameType());
        }
    }
}
