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

package cafe.jeffrey.profile.advisor.verify;

/**
 * The ladder of confidence a proposed patch can climb, in increasing order of cost and of what it
 * proves. Each level is only attempted when the one below it passed — there is nothing to learn from
 * compiling a patch that does not apply.
 */
public enum PatchCheckLevel {

    /** The unified diff applies cleanly to the analyzed checkout. */
    APPLIES("Applies cleanly"),

    /** The project still compiles with the patch applied. */
    COMPILES("Module compiles"),

    /** The project's tests still pass with the patch applied. */
    TESTS_PASS("Tests pass");

    private final String label;

    PatchCheckLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
