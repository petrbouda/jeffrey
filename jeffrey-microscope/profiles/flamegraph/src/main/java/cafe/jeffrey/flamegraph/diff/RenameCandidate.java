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
