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

package cafe.jeffrey.profile.advisor.prompt;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The frames of one profile, sorted by weight and looked up by name. Cached next to the prompt markdown
 * so that severity grading and claim grounding can run against the same numbers the model was shown,
 * without re-parsing the JFR file.
 *
 * <p>Lookup is deliberately forgiving about notation. A model asked about
 * {@code com/acme/order/RateTable.lookup} may cite it as {@code com.acme.order.RateTable.lookup},
 * {@code RateTable.lookup} or {@code RateTable#lookup} — all of which name the same measured frame, so
 * all of them resolve. What lookup will <em>not</em> do is invent a match for a frame that was never
 * sampled, which is the whole point of grounding.</p>
 */
public record ProfileFrameIndex(List<ProfileFrame> frames) {

    private static final String EMPTY_MARKER = "";

    public ProfileFrameIndex {
        frames = frames == null ? List.of() : List.copyOf(frames);
    }

    public static ProfileFrameIndex empty() {
        return new ProfileFrameIndex(List.of());
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    /**
     * The heaviest frame's share of the profile — the number severity is graded from.
     */
    public double dominantSharePct() {
        return frames.stream()
                .mapToDouble(ProfileFrame::totalPct)
                .max()
                .orElse(0.0);
    }

    /**
     * The {@code limit} heaviest frames, for rendering deterministic facts into the prompt.
     */
    public List<ProfileFrame> heaviest(int limit) {
        return frames.stream()
                .sorted(Comparator.comparingDouble(ProfileFrame::totalPct).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Resolves a frame name as cited by the model against the frames actually measured in this profile.
     * Returns empty when nothing matches, which marks the citation as ungrounded.
     */
    public Optional<ProfileFrame> find(String citedName) {
        String normalized = normalize(citedName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Map<String, ProfileFrame> byName = normalizedIndex();
        ProfileFrame exact = byName.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }

        // A bare "RateTable.lookup" should still resolve against the fully qualified measured frame,
        // but only when exactly one measured frame ends with it — an ambiguous suffix is not evidence.
        List<ProfileFrame> suffixMatches = byName.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("." + normalized))
                .map(Map.Entry::getValue)
                .toList();

        if (suffixMatches.size() == 1) {
            return Optional.of(suffixMatches.getFirst());
        }
        return Optional.empty();
    }

    private Map<String, ProfileFrame> normalizedIndex() {
        Map<String, ProfileFrame> byName = new LinkedHashMap<>();
        for (ProfileFrame frame : frames) {
            byName.putIfAbsent(normalize(frame.name()), frame);
        }
        return byName;
    }

    /**
     * Collapses the notations that all denote the same frame: package separators (slash vs dot), the
     * {@code Class#method} form, any parameter list, and case.
     */
    private static String normalize(String name) {
        if (name == null) {
            return EMPTY_MARKER;
        }
        String normalized = name.strip();
        int parenthesis = normalized.indexOf('(');
        if (parenthesis > 0) {
            normalized = normalized.substring(0, parenthesis);
        }
        return normalized
                .replace('/', '.')
                .replace('#', '.')
                .replace("::", ".")
                .toLowerCase();
    }
}
