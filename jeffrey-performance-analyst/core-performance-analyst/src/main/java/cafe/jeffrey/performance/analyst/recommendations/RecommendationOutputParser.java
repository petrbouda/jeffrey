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

import cafe.jeffrey.shared.common.model.Severity;

/**
 * Splits the model's single response into the artifacts the UI shows separately: the overall severity,
 * the recommendations markdown and the applicable patch. The model is instructed (see
 * {@link RecommendationPrompts}) to emit sections separated by the {@link #SEVERITY_MARKER},
 * {@link #RECOMMENDATIONS_MARKER} and {@link #PATCH_MARKER} marker lines; this parser is tolerant of a
 * missing severity section (defaults to {@link Severity#MEDIUM}), a missing patch section, a code-fenced
 * patch, and an explicit {@link #NO_PATCH_SENTINEL}.
 */
final class RecommendationOutputParser {

    static final String SEVERITY_MARKER = "===SEVERITY===";
    static final String RECOMMENDATIONS_MARKER = "===RECOMMENDATIONS===";
    static final String PATCH_MARKER = "===PATCH===";
    static final String NO_PATCH_SENTINEL = "(no patch)";

    private static final String FENCE = "```";

    private RecommendationOutputParser() {
    }

    static RecommendationResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new RecommendationResult(Severity.MEDIUM, "", null);
        }

        Severity severity = parseSeverity(raw);

        int patchMarker = raw.indexOf(PATCH_MARKER);
        if (patchMarker < 0) {
            // No patch section emitted — treat the whole response as recommendations.
            return new RecommendationResult(severity, stripRecommendationsMarker(raw).strip(), null);
        }

        String recommendations = stripRecommendationsMarker(raw.substring(0, patchMarker)).strip();
        String patch = normalizePatch(raw.substring(patchMarker + PATCH_MARKER.length()));
        return new RecommendationResult(severity, recommendations, patch);
    }

    /**
     * Reads the one-word severity from the {@link #SEVERITY_MARKER} section (between it and the
     * recommendations marker). Defaults to {@link Severity#MEDIUM} when the section is absent or unknown.
     */
    private static Severity parseSeverity(String raw) {
        int severityMarker = raw.indexOf(SEVERITY_MARKER);
        if (severityMarker < 0) {
            return Severity.MEDIUM;
        }
        int from = severityMarker + SEVERITY_MARKER.length();
        int recommendationsMarker = raw.indexOf(RECOMMENDATIONS_MARKER, from);
        String section = recommendationsMarker < 0 ? raw.substring(from) : raw.substring(from, recommendationsMarker);
        String firstToken = section.strip().split("\\s+", 2)[0];
        return Severity.fromString(firstToken);
    }

    private static String stripRecommendationsMarker(String text) {
        int marker = text.indexOf(RECOMMENDATIONS_MARKER);
        if (marker < 0) {
            return text;
        }
        return text.substring(marker + RECOMMENDATIONS_MARKER.length());
    }

    private static String normalizePatch(String rawPatch) {
        String patch = stripCodeFence(rawPatch.strip());
        if (patch.isBlank() || patch.equalsIgnoreCase(NO_PATCH_SENTINEL)) {
            return null;
        }
        // The model often miscounts hunk headers and drops the trailing newline; repair both so the
        // stored/downloaded patch actually applies with `git apply`.
        return UnifiedDiffNormalizer.normalize(patch);
    }

    /**
     * Removes a single wrapping code fence (```diff … ```), which the model sometimes adds around the
     * patch even though it is asked for a bare diff.
     */
    private static String stripCodeFence(String text) {
        if (!text.startsWith(FENCE)) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String withoutOpeningFence = text.substring(firstNewline + 1);
        int closingFence = withoutOpeningFence.lastIndexOf(FENCE);
        if (closingFence < 0) {
            return withoutOpeningFence.strip();
        }
        return withoutOpeningFence.substring(0, closingFence).strip();
    }
}
