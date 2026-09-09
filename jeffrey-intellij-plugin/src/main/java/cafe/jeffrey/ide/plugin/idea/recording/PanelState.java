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

package cafe.jeffrey.ide.plugin.idea.recording;

import java.util.List;

/**
 * Everything the panel knows, as the one value its renderers draw.
 *
 * <p>{@link RecordingState} answers about a single file and stays that way; a comparison is two of
 * them and a direction, which is what this adds. Keeping the pair here rather than as an eighth
 * component of {@code RecordingState} is what stops "the baseline's baseline" from being a shape the
 * types allow.
 *
 * <p><b>The direction is the field order and never moves.</b> {@code recording} is the file whose tab
 * this is — the primary, the run under examination — and {@code baseline} is what it is measured
 * against. Read the other way round every regression becomes an improvement, which is why Swap
 * exchanges the two files and reopens rather than flipping a flag somewhere downstream.
 *
 * @param recording  the file this tab was opened on
 * @param baseline   what it is compared against, or null when no comparison is set up
 * @param candidates the other recordings in this project, for the menu that picks a baseline;
 *                   empty until the scan behind it has answered, which is a menu of names rather
 *                   than no menu at all
 */
public record PanelState(
        RecordingState recording,
        RecordingState baseline,
        List<CompareCandidate> candidates) {

    public PanelState {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** A panel with nothing to compare against, which is how every tab opens. */
    public static PanelState of(RecordingState recording) {
        return new PanelState(recording, null, List.of());
    }

    public PanelState withRecording(RecordingState updated) {
        return new PanelState(updated, baseline, candidates);
    }

    public PanelState withBaseline(RecordingState updated) {
        return new PanelState(recording, updated, candidates);
    }

    public PanelState withCandidates(List<CompareCandidate> updated) {
        return new PanelState(recording, baseline, updated);
    }

    public boolean hasBaseline() {
        return baseline != null;
    }

    /**
     * Whether there is a pair to describe: both sides have a profile. A baseline still importing is
     * shown in the strip but has nothing to weigh yet.
     *
     * <p>This is what the strip, the second row of figures and the verdict key off. It is <b>not</b>
     * what decides where a link goes — see {@link #opensComparison()}.
     */
    public boolean isComparing() {
        return hasBaseline()
                && recording.status() == RecordingState.Status.READY
                && baseline.status() == RecordingState.Status.READY;
    }

    /**
     * Whether the differential pages can actually answer, which is stricter than having a pair.
     *
     * <p>A pair the verdict calls incomparable is still drawn — that verdict is the useful thing to
     * say about it — but every link must keep pointing at the primary's own views. Otherwise the
     * panel says "these two cannot be compared" and, in the same breath, replaces every tile with a
     * page that cannot be drawn and takes the primary's own views away.
     */
    public boolean opensComparison() {
        Comparability verdict = comparability();
        return verdict != null && verdict.level() != Comparability.Level.INCOMPARABLE;
    }

    /**
     * The verdict on the pair, or null when there is no pair to judge yet. Computed rather than
     * stored, so it cannot describe a baseline the panel has since replaced.
     */
    public Comparability comparability() {
        if (!isComparing()) {
            return null;
        }
        return Comparability.of(recording, baseline);
    }

    /** The views the tiles are drawn from: the differential pair while comparing, the profile's own otherwise. */
    public List<ProfileView> views() {
        RecordingState.ProfileSummary summary = recording.summary();
        if (opensComparison()) {
            return ProfileView.DIFFERENTIAL;
        }
        return summary == null ? List.of() : summary.views();
    }
}
