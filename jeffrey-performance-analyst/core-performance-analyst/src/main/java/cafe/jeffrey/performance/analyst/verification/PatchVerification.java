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

package cafe.jeffrey.performance.analyst.verification;

import java.util.List;
import java.util.Optional;

/**
 * Everything Jeffrey was able to establish about a proposed patch, plus the revision it was established
 * against. Stored with the recommendation so a patch loaded from cache carries the same evidence it had
 * when it was generated — the checkout it was verified against is long gone by then.
 *
 * @param checks           one entry per {@link PatchCheckLevel}, in ladder order
 * @param verifiedAgainstRef the commit the checks ran against, or null when the revision was not pinned
 */
public record PatchVerification(List<PatchCheck> checks, String verifiedAgainstRef) {

    public PatchVerification {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public static PatchVerification notAttempted() {
        return new PatchVerification(List.of(), null);
    }

    public boolean isEmpty() {
        return checks.isEmpty();
    }

    /**
     * True when the diff was confirmed to apply to the analyzed checkout. This is the one claim the UI
     * makes prominently, because it is the difference between a patch and a suggestion.
     */
    public boolean applies() {
        return check(PatchCheckLevel.APPLIES).map(PatchCheck::isPassed).orElse(false);
    }

    /**
     * The strongest level that actually passed — what the UI summarises the patch as.
     */
    public Optional<PatchCheckLevel> highestPassed() {
        return checks.stream()
                .filter(PatchCheck::isPassed)
                .map(PatchCheck::level)
                .reduce((first, second) -> second);
    }

    public Optional<PatchCheck> check(PatchCheckLevel level) {
        return checks.stream().filter(check -> check.level() == level).findFirst();
    }
}
