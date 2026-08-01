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

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the model's single response into the artifacts the UI shows separately: the claims each
 * recommendation rests on, the recommendations markdown, and the proposed patch. The model is
 * instructed (see {@link AdvisorPrompts}) to emit sections separated by the {@link #CLAIMS_MARKER},
 * {@link #RECOMMENDATIONS_MARKER} and {@link #PATCH_MARKER} marker lines; this parser is tolerant of a
 * missing claims or patch section.
 *
 * <p>There is deliberately no severity section any more. Severity is computed from the measured profile
 * by {@link SeverityCalculator}, so this parser has nothing to guess at and no reason to fall back to a
 * default that hid parse failures.</p>
 */
final class AdvisorOutputParser {

    static final String CLAIMS_MARKER = "===CLAIMS===";
    static final String RECOMMENDATIONS_MARKER = "===RECOMMENDATIONS===";
    static final String PATCH_MARKER = "===PATCH===";

    /** What the model is told to write when it has no concrete edit to propose. */
    private static final String NO_PATCH_SENTINEL = "(no patch)";

    private static final String FENCE = "```";

    private static final String CLAIM_FIELD_SEPARATOR = "\\|";
    private static final int CLAIM_FIELD_FRAME = 0;
    private static final int CLAIM_FIELD_SOURCE = 1;
    private static final int CLAIM_FIELD_TITLE = 2;

    private AdvisorOutputParser() {
    }

    /**
     * The raw sections of a model response, before any of them have been checked against evidence.
     */
    record ParsedOutput(List<AdvisorClaim> claims, String recommendations, String patch) {

        ParsedOutput {
            claims = claims == null ? List.of() : List.copyOf(claims);
        }

        boolean hasPatch() {
            return patch != null && !patch.isBlank();
        }
    }

    static ParsedOutput parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedOutput(List.of(), "", null);
        }

        List<AdvisorClaim> claims = parseClaims(raw);
        String afterClaims = stripThrough(raw, CLAIMS_MARKER);
        String afterRecommendations = stripThrough(afterClaims, RECOMMENDATIONS_MARKER);
        String recommendations = beforeMarker(afterRecommendations, PATCH_MARKER).strip();
        return new ParsedOutput(claims, recommendations, parsePatch(raw));
    }

    /**
     * The unified diff, repaired so it can actually be applied — or null when the model said it had no
     * edit to propose. A blank section is treated the same as the sentinel: silence is not a patch.
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
        return UnifiedDiffNormalizer.normalize(section);
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

    /**
     * Reads the {@code frame | source | title} lines between the claims and recommendations markers.
     * A line that names no frame is skipped rather than rejected — a malformed claim should cost the
     * model that one citation, not the whole report.
     */
    private static List<AdvisorClaim> parseClaims(String raw) {
        int claimsMarker = raw.indexOf(CLAIMS_MARKER);
        if (claimsMarker < 0) {
            return List.of();
        }

        int from = claimsMarker + CLAIMS_MARKER.length();
        int end = raw.indexOf(RECOMMENDATIONS_MARKER, from);
        String section = end < 0 ? raw.substring(from) : raw.substring(from, end);

        List<AdvisorClaim> claims = new ArrayList<>();
        for (String rawLine : section.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || !line.contains("|")) {
                continue;
            }
            String[] fields = line.split(CLAIM_FIELD_SEPARATOR, -1);
            String frame = fields[CLAIM_FIELD_FRAME].strip();
            if (frame.isEmpty()) {
                continue;
            }
            claims.add(new AdvisorClaim(
                    field(fields, CLAIM_FIELD_TITLE), frame, emptyToNull(field(fields, CLAIM_FIELD_SOURCE))));
        }
        return claims;
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].strip() : "";
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String stripThrough(String text, String marker) {
        int index = text.indexOf(marker);
        if (index < 0) {
            return text;
        }
        return text.substring(index + marker.length());
    }
}
