/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
