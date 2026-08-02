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

package cafe.jeffrey.profile.advisor.run;

/**
 * Splits the model's single response into the two artifacts the UI shows separately: the
 * recommendations markdown and the proposed patch. The model is instructed (see
 * {@code AdvisorPrompts}) to emit sections separated by the {@link #RECOMMENDATIONS_MARKER} and
 * {@link #PATCH_MARKER} marker lines; this parser is tolerant of a missing patch section.
 *
 * <p>There is deliberately no severity section. Severity is computed from the measured profile by
 * {@link SeverityCalculator}, so this parser has nothing to guess at and no reason to fall back to a
 * default that hid parse failures.</p>
 */
final class AdvisorOutputParser {

    static final String RECOMMENDATIONS_MARKER = "===RECOMMENDATIONS===";
    static final String PATCH_MARKER = "===PATCH===";

    /** What the model is told to write when it has no concrete edit to propose. */
    private static final String NO_PATCH_SENTINEL = "(no patch)";

    private static final String FENCE = "```";

    private AdvisorOutputParser() {
    }

    /**
     * The sections of a model response. A response that carries no marker at all yields its whole body
     * as the recommendations, so a model that ignored the format still says something useful.
     */
    record ParsedOutput(String recommendations, String patch) {

        boolean hasPatch() {
            return patch != null && !patch.isBlank();
        }
    }

    static ParsedOutput parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedOutput("", null);
        }

        String afterRecommendations = stripThrough(raw, RECOMMENDATIONS_MARKER);
        String recommendations = beforeMarker(afterRecommendations, PATCH_MARKER).strip();
        return new ParsedOutput(recommendations, parsePatch(raw));
    }

    /**
     * The diff section exactly as the model wrote it, unwrapped from any fence — or null when it said
     * it had no edit to propose. A blank section is treated the same as the sentinel: silence is not a
     * patch. Repairing the diff and checking it against the checkout is {@link PatchBuilder}'s job, and
     * is reported as its own step in the run timeline.
     */
    private static String parsePatch(String raw) {
        int marker = raw.indexOf(PATCH_MARKER);
        if (marker < 0) {
            return null;
        }
        String section = stripFence(raw.substring(marker + PATCH_MARKER.length()).strip());
        if (section.isEmpty() || section.equalsIgnoreCase(NO_PATCH_SENTINEL)) {
            return null;
        }
        return section;
    }

    /**
     * Unwraps a fenced block the model added despite being told not to. Only a fence that both opens
     * and closes the whole section is stripped — a stray fence inside a diff is left alone, because
     * removing it would corrupt the body.
     */
    private static String stripFence(String section) {
        if (!section.startsWith(FENCE)) {
            return section;
        }
        int firstLineEnd = section.indexOf('\n');
        if (firstLineEnd < 0) {
            return section;
        }
        int closing = section.lastIndexOf(FENCE);
        if (closing <= firstLineEnd) {
            return section;
        }
        return section.substring(firstLineEnd + 1, closing).strip();
    }

    private static String beforeMarker(String text, String marker) {
        int index = text.indexOf(marker);
        return index < 0 ? text : text.substring(0, index);
    }

    private static String stripThrough(String text, String marker) {
        int index = text.indexOf(marker);
        if (index < 0) {
            return text;
        }
        return text.substring(index + marker.length());
    }
}
