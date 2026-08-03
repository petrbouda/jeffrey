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

package cafe.jeffrey.profile.advisor.prompt;

/**
 * The single heaviest method of a profile and its share of it. The share is what severity is graded
 * from; the name is what makes the grade mean something to a person, so the two travel together rather
 * than the name being recomputed — or, as it used to be, discarded.
 *
 * @param method  the fully qualified method name, or {@code ""} when the profile has no samples
 * @param selfPct the method's self samples as a percentage of the profile's total
 */
public record DominantMethod(String method, double selfPct) {

    private static final String NO_METHOD = "";

    /** What an empty call tree produces: nothing is dominant, and nothing is graded. */
    public static final DominantMethod NONE = new DominantMethod(NO_METHOD, 0.0);

    public DominantMethod {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
    }
}
