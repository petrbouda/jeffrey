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

import cafe.jeffrey.profile.advisor.prompt.ProfileFrame;
import cafe.jeffrey.profile.advisor.prompt.ProfileFrameIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives the facts about a profile that need no interpretation, so the model is handed them instead of
 * rediscovering them. A model asked to find the hotspots will spend budget re-reading the call tree and
 * will occasionally get an arithmetic answer wrong; a model handed the hotspots spends that budget on
 * the part only it can do, which is mapping them to source.
 *
 * <p>Every statement here is computed from the measured {@link ProfileFrameIndex}. Nothing is inferred,
 * which is what makes it safe to present to the model as established.</p>
 */
public final class ProfileFactsBuilder {

    private static final int TOP_HOTSPOT_COUNT = 5;
    private static final double MIN_REPORTABLE_SELF_PCT = 1.0;

    private static final String HEADING = "## Verified findings";
    private static final String PREAMBLE = """
            These were measured by Jeffrey from the recording, not inferred. Treat them as established
            fact: do not re-derive them, do not contradict them, and cite them by frame name when a
            recommendation rests on one.""";
    private static final String NO_FACTS = "- No frame accounts for a reportable share of this profile.";

    private ProfileFactsBuilder() {
    }

    public static String build(ProfileFrameIndex index) {
        StringBuilder out = new StringBuilder(1024);
        out.append(HEADING).append('\n').append('\n').append(PREAMBLE).append('\n').append('\n');

        List<String> facts = facts(index);
        if (facts.isEmpty()) {
            out.append(NO_FACTS).append('\n');
            return out.toString();
        }
        for (String fact : facts) {
            out.append("- ").append(fact).append('\n');
        }
        return out.toString();
    }

    private static List<String> facts(ProfileFrameIndex index) {
        List<String> facts = new ArrayList<>();
        for (ProfileFrame frame : index.heaviest(TOP_HOTSPOT_COUNT)) {
            if (frame.selfPct() < MIN_REPORTABLE_SELF_PCT) {
                continue;
            }
            facts.add(hotspotFact(frame));
            if (frame.stuckAtC1()) {
                facts.add(jitFact(frame));
            }
        }
        return facts;
    }

    private static String hotspotFact(ProfileFrame frame) {
        String character = frame.isLeafHotspot()
                ? "the cost is in this method itself, not in what it calls"
                : "the cost is mostly in what this method calls, not in the method itself";
        return String.format(
                Locale.ROOT,
                "`%s` accounts for %.1f%% of samples in itself and %.1f%% including its callees — %s.",
                frame.name(), frame.selfPct(), frame.totalPct(), character);
    }

    private static String jitFact(ProfileFrame frame) {
        return String.format(
                Locale.ROOT,
                "`%s` ran C1-compiled for %d samples and was never promoted to C2 — a JIT-tier signal, "
                        + "not necessarily an algorithmic problem.",
                frame.name(), frame.c1Samples());
    }
}
