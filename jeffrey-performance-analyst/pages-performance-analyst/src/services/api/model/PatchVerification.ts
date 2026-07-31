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

export type PatchCheckLevel = 'APPLIES' | 'COMPILES' | 'TESTS_PASS';

/**
 * SKIPPED is distinct from PASSED on purpose: a check that could not run proves nothing, and showing
 * it as a success would be the one bug that makes the whole verification feature untrustworthy.
 */
export type PatchCheckStatus = 'PASSED' | 'FAILED' | 'SKIPPED';

export interface PatchCheck {
  level: PatchCheckLevel;
  status: PatchCheckStatus;
  /** Failure output when the check failed, or the reason it could not run when skipped. */
  detail: string | null;
}

/**
 * What Jeffrey established about a proposed patch, against the revision named by `verifiedAgainstRef`.
 * An empty `checks` array means verification was never attempted — which the UI must not present as
 * the same thing as a patch that was checked and passed nothing.
 */
export default interface PatchVerification {
  checks: PatchCheck[];
  verifiedAgainstRef: string | null;
}
