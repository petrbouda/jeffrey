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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs the unified diff an LLM produces so it actually applies with {@code git apply}. Models reliably
 * get the edit content right but routinely miscount the {@code @@ -a,b +c,d @@} hunk line numbers and omit
 * the trailing newline, which {@code git apply} rejects as a corrupt patch. This normalizer recomputes
 * each hunk's old/new line counts from the real body (the same fix as {@code git apply --recount}),
 * preserving the start offsets and section heading, and guarantees a single trailing newline.
 *
 * <p>It does not invent content: non-hunk lines (file headers, {@code diff --git} lines, etc.) pass
 * through untouched, and a payload with no hunk headers is returned unchanged apart from the trailing
 * newline. It also cannot fix wrong start offsets — only the counts — but those are what break in
 * practice.
 */
final class UnifiedDiffNormalizer {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)$");

    private static final char ADDED = '+';
    private static final char REMOVED = '-';
    private static final char NO_NEWLINE_MARKER = '\\';

    private UnifiedDiffNormalizer() {
    }

    static String normalize(String diff) {
        // Work line-by-line without the trailing newline so we can re-add exactly one at the end.
        String body = diff.endsWith("\n") ? diff.substring(0, diff.length() - 1) : diff;
        String[] lines = body.split("\n", -1);

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            Matcher header = HUNK_HEADER.matcher(lines[i]);
            if (!header.matches()) {
                out.append(lines[i]).append('\n');
                i++;
                continue;
            }

            int oldStart = Integer.parseInt(header.group(1));
            int newStart = Integer.parseInt(header.group(2));
            String heading = header.group(3);

            StringBuilder hunkBody = new StringBuilder();
            int oldCount = 0;
            int newCount = 0;
            int j = i + 1;
            while (j < lines.length && !isBoundary(lines[j])) {
                String line = lines[j];
                char marker = line.isEmpty() ? ' ' : line.charAt(0);
                if (marker == ADDED) {
                    newCount++;
                } else if (marker == REMOVED) {
                    oldCount++;
                } else if (marker == NO_NEWLINE_MARKER) {
                    // "\ No newline at end of file" — belongs to the hunk but counts toward neither side.
                } else {
                    // Context line (leading space, or a bare blank line git treats as context).
                    oldCount++;
                    newCount++;
                }
                hunkBody.append(line).append('\n');
                j++;
            }

            out.append("@@ -").append(oldStart).append(',').append(oldCount)
                    .append(" +").append(newStart).append(',').append(newCount)
                    .append(" @@").append(heading).append('\n');
            out.append(hunkBody);
            i = j;
        }
        return out.toString();
    }

    /**
     * A new hunk or a file-level header ends the current hunk body. A removed line ({@code -x}) is not a
     * boundary because it cannot match {@code "--- "} (three dashes then a space).
     */
    private static boolean isBoundary(String line) {
        return line.startsWith("@@ ")
                || line.startsWith("diff --git ")
                || line.startsWith("--- ")
                || line.startsWith("+++ ")
                || line.startsWith("index ");
    }
}
