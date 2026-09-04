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

package cafe.jeffrey.flamegraph.diff;

/**
 * A subtree that appeared and one of about the same size that vanished — possibly the same work under
 * a new name.
 * <p>
 * The diff tree is built by matching method names level by level, so a rename, a moved method or an
 * extracted helper severs the match: the work shows up once as brand-new and once as entirely gone.
 * Read literally that is a dramatic finding ("we added a 4-second call path and deleted another"), and
 * a reader with only the profile in front of them has no way to tell it from a real change.
 * <p>
 * This pairing is deliberately a <em>suspicion</em>, not a resolution. Matching on weight alone cannot
 * distinguish a rename from a coincidence, and quietly folding the two entries into one would erase a
 * genuine change whenever it guessed wrong. It is reported so the reader — who has the source diff,
 * which this does not — can confirm or dismiss it in a second.
 *
 * @param appearedMethod   root method of the subtree present only in the primary
 * @param appearedPath     where that subtree hangs in the call tree
 * @param appearedMeasure  its measurement in the primary
 * @param vanishedMethod   root method of the subtree present only in the baseline
 * @param vanishedPath     where that subtree hung in the baseline
 * @param vanishedMeasure  its measurement in the baseline, scaled onto the primary's time base
 */
public record RenameCandidate(
        String appearedMethod,
        String appearedPath,
        long appearedMeasure,
        String vanishedMethod,
        String vanishedPath,
        long vanishedMeasure) {
}
