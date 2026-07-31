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
 * The outcome of one {@link PatchCheckLevel}.
 *
 * <p>{@link #SKIPPED} is deliberately distinct from {@link #PASSED}. A check that could not run — no
 * build command configured, no {@code git} on the host, a level below it already failed — proves
 * nothing, and reporting it as success would be the one bug that undermines the entire feature.</p>
 */
public enum PatchCheckStatus {
    PASSED,
    FAILED,
    SKIPPED
}
