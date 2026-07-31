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

package cafe.jeffrey.profile.advisor.regression;

import cafe.jeffrey.profile.advisor.prompt.ProfileFrame;
import cafe.jeffrey.profile.advisor.prompt.ProfileFrameIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two profiles frame by frame.
 *
 * <p>Deliberately a pure function with no model call anywhere near it. "Did this get slower" is
 * arithmetic over two measurements, and an arithmetic question answered by a language model is a
 * question answered less reliably and less reproducibly than it needs to be. The model's job starts
 * afterwards, on the much smaller set of frames that actually moved.</p>
 */
public final class ProfileComparator {

    private ProfileComparator() {
    }

    /**
     * The frames whose self share moved by at least {@code thresholdPp} percentage points. Frames absent
     * from one side are treated as zero there, so newly appearing cost shows up rather than being
     * silently skipped for lack of a counterpart.
     */
    public static ProfileComparison compare(
            String eventType,
            ProfileFrameIndex baseline,
            ProfileFrameIndex current,
            double thresholdPp) {

        Map<String, Double> baselineShares = sharesByName(baseline);
        Map<String, Double> currentShares = sharesByName(current);

        Set<String> allFrames = new LinkedHashSet<>(currentShares.keySet());
        allFrames.addAll(baselineShares.keySet());

        List<FrameDelta> regressed = new ArrayList<>();
        List<FrameDelta> improved = new ArrayList<>();

        for (String name : allFrames) {
            FrameDelta delta = new FrameDelta(
                    name,
                    baselineShares.getOrDefault(name, 0.0),
                    currentShares.getOrDefault(name, 0.0));

            if (Math.abs(delta.deltaPp()) < thresholdPp) {
                continue;
            }
            if (delta.isRegression()) {
                regressed.add(delta);
            } else {
                improved.add(delta);
            }
        }

        regressed.sort(Comparator.comparingDouble(FrameDelta::deltaPp).reversed());
        improved.sort(Comparator.comparingDouble(FrameDelta::deltaPp));

        return new ProfileComparison(eventType, regressed, improved, thresholdPp);
    }

    private static Map<String, Double> sharesByName(ProfileFrameIndex index) {
        return index.frames().stream()
                .collect(Collectors.toMap(
                        ProfileFrame::name,
                        ProfileFrame::selfPct,
                        Math::max,
                        LinkedHashMap::new));
    }

    /**
     * Renders the moved frames as the profile fragment a follow-up analysis is scoped to. Handing the
     * model only what changed is both far cheaper than a whole profile and far more focused: the
     * question is no longer "what is slow" but "which of these specific changes explains this".
     */
    public static String toPromptFragment(ProfileComparison comparison) {
        StringBuilder out = new StringBuilder(512);
        out.append("## Measured regressions\n\n");
        out.append("These frames grew between the baseline recording and the current one. Shares are ")
                .append("percentages of total samples; the delta is in percentage points.\n\n");

        if (comparison.regressed().isEmpty()) {
            out.append("- No frame moved by at least ")
                    .append(comparison.thresholdPp())
                    .append(" percentage points.\n");
            return out.toString();
        }

        for (FrameDelta delta : comparison.regressed()) {
            out.append(String.format(
                    Locale.ROOT,
                    "- `%s`: %.1f%% → %.1f%% (%+.1f pp)%s%n",
                    delta.name(), delta.baselineSelfPct(), delta.currentSelfPct(), delta.deltaPp(),
                    delta.isNew() ? " — absent from the baseline entirely" : ""));
        }
        return out.toString();
    }
}
