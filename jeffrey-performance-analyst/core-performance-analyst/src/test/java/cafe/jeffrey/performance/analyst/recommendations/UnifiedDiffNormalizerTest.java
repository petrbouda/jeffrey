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

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffNormalizerTest {

    private static final Pattern NORMALIZED_HEADER =
            Pattern.compile("^@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@");

    @Test
    void recountsWrongHunkCountsAndAddsTrailingNewline() {
        // Header lies (1/1) and there is no trailing newline — the two defects from the reported patch.
        String broken = "--- a/Foo.java\n"
                + "+++ b/Foo.java\n"
                + "@@ -10,1 +10,1 @@\n"
                + " context one\n"
                + " context two\n"
                + "+added line\n"
                + " context three\n"
                + "-removed line\n"
                + "+replacement\n"
                + " context four";

        String fixed = UnifiedDiffNormalizer.normalize(broken);

        // old = 4 context + 1 removed = 5; new = 4 context + 2 added = 6.
        assertTrue(fixed.contains("@@ -10,5 +10,6 @@"), fixed);
        assertTrue(fixed.endsWith("\n"), "patch must end with a newline");
        assertCountsConsistent(fixed);
    }

    @Test
    void recountsMergedMultiRegionHunkWithBlankContextLines() {
        // One header spanning two edit regions separated by unchanged (incl. bare blank) context lines —
        // the exact shape the model produced for the reported patch.
        String broken = "--- a/A.java\n"
                + "+++ b/A.java\n"
                + "@@ -19,6 +19,7 @@ import java.util.Optional;\n"
                + " import a;\n"
                + "+import b;\n"
                + " import c;\n"
                + "\n"
                + " class A {\n"
                + "\n"
                + "+    int field;\n"
                + "+\n"
                + " }\n";

        String fixed = UnifiedDiffNormalizer.normalize(broken);

        // context: import a, import c, blank, class A {, blank, } = 6; added: import b, int field, blank = 3.
        assertTrue(fixed.contains("@@ -19,6 +19,9 @@ import java.util.Optional;"), fixed);
        assertCountsConsistent(fixed);
    }

    @Test
    void preservesNonDiffPayloadAndGuaranteesNewline() {
        String notReallyAHunk = "diff --git a/A b/A\n+x";

        String fixed = UnifiedDiffNormalizer.normalize(notReallyAHunk);

        assertEquals("diff --git a/A b/A\n+x\n", fixed);
    }

    @Test
    void expandsBareStartIntoExplicitCount() {
        String broken = "@@ -1 +1 @@\n-slow\n+fast\n";

        String fixed = UnifiedDiffNormalizer.normalize(broken);

        assertTrue(fixed.startsWith("@@ -1,1 +1,1 @@"), fixed);
        assertCountsConsistent(fixed);
    }

    /**
     * Asserts every hunk header's declared old/new counts equal the actual body line counts — the
     * invariant {@code git apply} enforces.
     */
    private static void assertCountsConsistent(String diff) {
        // Drop the single trailing newline so its split artifact isn't counted as a body line.
        String body = diff.endsWith("\n") ? diff.substring(0, diff.length() - 1) : diff;
        String[] lines = body.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher header = NORMALIZED_HEADER.matcher(lines[i]);
            if (!header.matches()) {
                continue;
            }
            int declaredOld = Integer.parseInt(header.group(2));
            int declaredNew = Integer.parseInt(header.group(4));
            int actualOld = 0;
            int actualNew = 0;
            int j = i + 1;
            while (j < lines.length && !lines[j].startsWith("@@ ") && !lines[j].startsWith("--- ")
                    && !lines[j].startsWith("+++ ") && !lines[j].startsWith("diff --git ")) {
                char marker = lines[j].isEmpty() ? ' ' : lines[j].charAt(0);
                if (marker == '+') {
                    actualNew++;
                } else if (marker == '-') {
                    actualOld++;
                } else if (marker != '\\') {
                    actualOld++;
                    actualNew++;
                }
                j++;
            }
            assertEquals(declaredOld, actualOld, "old count mismatch in: " + lines[i]);
            assertEquals(declaredNew, actualNew, "new count mismatch in: " + lines[i]);
        }
    }
}
