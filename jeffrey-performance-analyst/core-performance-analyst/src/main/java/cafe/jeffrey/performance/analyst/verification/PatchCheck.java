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

/**
 * The result of one verification level, with the reason attached. {@code detail} carries the failure
 * output when the check failed and the reason it could not run when it was skipped — both are what the
 * person reading the patch needs in order to decide what to do next.
 *
 * @param level  which check this is
 * @param status how it went
 * @param detail failure output or skip reason; null when the check simply passed
 */
public record PatchCheck(PatchCheckLevel level, PatchCheckStatus status, String detail) {

    private static final int MAX_DETAIL_LENGTH = 4_000;

    public PatchCheck {
        if (level == null || status == null) {
            throw new IllegalArgumentException("A patch check needs both a level and a status");
        }
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_DETAIL_LENGTH) + "\n… (truncated)";
        }
    }

    public static PatchCheck passed(PatchCheckLevel level) {
        return new PatchCheck(level, PatchCheckStatus.PASSED, null);
    }

    public static PatchCheck failed(PatchCheckLevel level, String detail) {
        return new PatchCheck(level, PatchCheckStatus.FAILED, detail);
    }

    public static PatchCheck skipped(PatchCheckLevel level, String reason) {
        return new PatchCheck(level, PatchCheckStatus.SKIPPED, reason);
    }

    public boolean isPassed() {
        return status == PatchCheckStatus.PASSED;
    }
}
