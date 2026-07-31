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

package cafe.jeffrey.performance.analyst.recommendations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrame;
import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrameIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimGrounderTest {

    @TempDir
    Path repositoryRoot;

    private ProfileFrameIndex index;

    @BeforeEach
    void setUp() throws IOException {
        index = new ProfileFrameIndex(List.of(
                new ProfileFrame("com/acme/order/RateTable.lookup", 23.4, 21.0, 4200, 0, 4200, 0),
                new ProfileFrame("java/math/BigDecimal.valueOf", 6.1, 5.8, 1100, 0, 0, 1100)));

        Files.createDirectories(repositoryRoot.resolve("src/main/java/com/acme"));
        Files.writeString(repositoryRoot.resolve("src/main/java/com/acme/RateTable.java"), "class RateTable {}");
    }

    private ClaimGrounder grounder() {
        return new ClaimGrounder(index, repositoryRoot);
    }

    private static RecommendationClaim claim(String frame, String source) {
        return new RecommendationClaim("A finding", frame, source);
    }

    @Nested
    class FrameResolution {

        @Test
        void groundsAnExactFrameName() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("com/acme/order/RateTable.lookup", null)))
                    .getFirst();

            assertTrue(result.grounded());
            assertEquals(23.4, result.frame().totalPct());
        }

        @Test
        void groundsDotNotationAgainstSlashNotation() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("com.acme.order.RateTable.lookup", null)))
                    .getFirst();

            assertTrue(result.grounded());
        }

        @Test
        void groundsAnUnambiguousBareSuffix() {
            GroundedClaim result = grounder().ground(List.of(claim("RateTable.lookup", null))).getFirst();

            assertTrue(result.grounded());
        }

        @Test
        void groundsTheHashFormAndIgnoresAParameterList() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("com/acme/order/RateTable#lookup(String)", null)))
                    .getFirst();

            assertTrue(result.grounded());
        }

        @Test
        void refusesAFrameThatWasNeverSampled() {
            GroundedClaim result = grounder().ground(List.of(claim("com/acme/Imaginary.method", null))).getFirst();

            assertFalse(result.grounded());
        }

        @Test
        void refusesAnAmbiguousSuffix() {
            index = new ProfileFrameIndex(List.of(
                    new ProfileFrame("com/acme/a/Table.lookup", 5, 5, 100, 0, 0, 100),
                    new ProfileFrame("com/acme/b/Table.lookup", 5, 5, 100, 0, 0, 100)));

            // Two measured frames end with the same suffix, so a bare citation names neither of them
            // in particular — and a coin flip is not evidence.
            GroundedClaim result = grounder().ground(List.of(claim("Table.lookup", null))).getFirst();

            assertFalse(result.grounded());
        }
    }

    @Nested
    class SourceResolution {

        @Test
        void findsAnExistingSourceFile() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("RateTable.lookup", "src/main/java/com/acme/RateTable.java")))
                    .getFirst();

            assertTrue(result.sourceFound());
        }

        @Test
        void acceptsATrailingLineNumber() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("RateTable.lookup", "src/main/java/com/acme/RateTable.java:88")))
                    .getFirst();

            assertTrue(result.sourceFound());
        }

        @Test
        void reportsAMissingSourceFile() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("RateTable.lookup", "src/main/java/com/acme/Nope.java")))
                    .getFirst();

            assertTrue(result.grounded(), "the frame is still real even when the path is wrong");
            assertFalse(result.sourceFound());
        }

        @Test
        void refusesAPathThatEscapesTheCheckout() {
            GroundedClaim result = grounder()
                    .ground(List.of(claim("RateTable.lookup", "../../../etc/passwd")))
                    .getFirst();

            assertFalse(result.sourceFound());
        }
    }

    @Nested
    class EmptyIndex {

        @Test
        void groundsNothingWhenTheProfileIndexIsMissing() {
            // Prompts cached before the index existed have no frames to check against; every claim is
            // reported as unverified rather than silently trusted.
            ClaimGrounder grounder = new ClaimGrounder(ProfileFrameIndex.empty(), repositoryRoot);

            assertFalse(grounder.ground(List.of(claim("RateTable.lookup", null))).getFirst().grounded());
        }
    }
}
