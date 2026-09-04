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

package cafe.jeffrey.microscope.core.mcp.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the next answer lives, carried back beside the figures.
 * <p>
 * A model picks its next call from what it has just read, and the tool description that would have
 * told it was read many turns earlier. {@code JvmSection} established this for the machine-level
 * dashboards; this is the same envelope for every other family.
 * <p>
 * The rule these lines follow, unchanged from that one: <em>they route, they never diagnose.</em> A
 * line says what this answer cannot tell you and which tool can. None of them claims the figures
 * above are bad.
 * <p>
 * {@link Builder#when} is the one concession, and it is not a threshold. It gates a line on a
 * phenomenon having <em>occurred</em> — a pool timed out, a request failed — the same kind of
 * question {@code JvmSections.isAvailable} already asks about recorded event types. "It happened, and
 * here is what explains it" is still routing; "it happened too often" would be a verdict, and no line
 * here is allowed to make one.
 */
final class NextSteps {

    private NextSteps() {
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private final List<String> steps = new ArrayList<>();

        /**
         * A line that belongs on every answer of this kind.
         */
        Builder add(String step) {
            steps.add(step);
            return this;
        }

        /**
         * A line that belongs only when the thing it talks about actually happened.
         */
        Builder when(boolean occurred, String step) {
            if (occurred) {
                steps.add(step);
            }
            return this;
        }

        List<String> build() {
            return List.copyOf(steps);
        }
    }
}
