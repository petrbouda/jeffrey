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

package cafe.jeffrey.profile.mcp.finding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What can be done with findings once they all share one shape: give them a stable identity, fold
 * several tools' lists into one, and count them by severity.
 */
public final class McpFindings {

    private static final String ID_SEPARATOR = ":";
    private static final String DEFAULT_SUBJECT = "general";
    private static final String SLUG_DASH = "-";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-|-$)");

    private McpFindings() {
    }

    /**
     * {@code category:subject}, both lower-cased and the subject reduced to a dash-separated slug, so
     * {@code Long GC Pauses} and {@code long-gc-pauses} name the same finding.
     */
    public static String id(String category, String subject) {
        String slug = subject == null || subject.isBlank()
                ? DEFAULT_SUBJECT
                : EDGE_DASHES.matcher(
                        NON_ALPHANUMERIC.matcher(subject.toLowerCase(Locale.ROOT)).replaceAll(SLUG_DASH))
                        .replaceAll("");
        return category.toLowerCase(Locale.ROOT) + ID_SEPARATOR + slug;
    }

    /**
     * One list from several, de-duplicated by id and ordered by severity. Where two tools report the
     * same id the more severe one is kept, and on a tie the first — so the source that spoke first
     * keeps its wording and a later one cannot overwrite it with a repeat.
     */
    @SafeVarargs
    public static List<McpFinding> merge(List<McpFinding>... lists) {
        Map<String, McpFinding> byId = new LinkedHashMap<>();
        for (List<McpFinding> list : lists) {
            if (list == null) {
                continue;
            }
            for (McpFinding finding : list) {
                if (finding == null) {
                    continue;
                }
                byId.merge(finding.id(), finding, McpFindings::moreSevere);
            }
        }
        List<McpFinding> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(McpFinding::severity));
        return List.copyOf(merged);
    }

    private static McpFinding moreSevere(McpFinding existing, McpFinding candidate) {
        return candidate.severity().outranks(existing.severity()) ? candidate : existing;
    }

    /**
     * How many findings of each severity, every severity present even at zero, so a reader can see
     * "no warnings" rather than infer it from a missing key.
     */
    public static Map<String, Integer> countBySeverity(List<McpFinding> findings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (McpFinding.Severity severity : McpFinding.Severity.values()) {
            counts.put(severity.name(), 0);
        }
        for (McpFinding finding : findings) {
            counts.merge(finding.severity().name(), 1, Integer::sum);
        }
        return counts;
    }
}
