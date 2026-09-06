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

package cafe.jeffrey.profile.ai.chat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceGroundingTest {

    /**
     * The section is wrapped prose, so a sentence spans lines at whatever column it happens to reach.
     * These tests are about what it says, not where it breaks.
     */
    private static String unwrapped(String section) {
        return section.replaceAll("\\s+", " ");
    }

    @Nested
    class WithoutSources {

        /**
         * The property this exists for: an analysis that was given no checkout must not be told about
         * one. A prompt describing tools the run cannot use is worse than saying nothing, because the
         * model spends turns trying.
         */
        @Test
        void saysNothingAtAll() {
            assertEquals("", SourceGrounding.section(null));
        }
    }

    @Nested
    class WithSources {

        @Test
        void namesTheDirectoryTheRunActuallyHas() {
            String section = SourceGrounding.section(new SourceAccess(Path.of("/code/order-service")));

            assertTrue(section.contains("/code/order-service"));
        }

        @Test
        void namesTheToolsItMayUseAndTheOnesItMayNot() {
            String section = unwrapped(SourceGrounding.section(new SourceAccess(Path.of("/code/x"))));

            assertTrue(section.contains("Read, Grep and Glob"));
            assertTrue(section.contains("no Edit, no Write and no shell"),
                    "the run has no way to change the working tree, and the model should know");
        }

        @Test
        void carriesTheRuleThatSeparatesReadingFromGuessing() {
            String section = unwrapped(SourceGrounding.section(new SourceAccess(Path.of("/code/x"))));

            assertTrue(section.contains("Open the file before naming it"));
        }

        /**
         * A path is an argument, never part of the format string — a directory with a percent sign in
         * it must not be read as a conversion.
         */
        @Test
        void aPathWithAPercentSignIsNotAFormatSpecifier() {
            String section = SourceGrounding.section(new SourceAccess(Path.of("/code/100%done")));

            assertTrue(section.contains("/code/100%done"));
        }
    }
}
