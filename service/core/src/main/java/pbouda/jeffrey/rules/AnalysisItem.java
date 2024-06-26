/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.rules;

import org.openjdk.jmc.flightrecorder.rules.IRule;

public record AnalysisItem(
        String rule,
        Severity severity,
        String explanation,
        String summary,
        String solution,
        String score) {

    public enum Severity {
        OK(5), WARNING(1), NA(3), INFO(2), IGNORE(4);

        private final int order;

        Severity(int order) {
            this.order = order;
        }

        public int order() {
            return order;
        }
    }

    public AnalysisItem(
            IRule rule,
            org.openjdk.jmc.flightrecorder.rules.Severity severity,
            String explanation,
            String summary,
            String solution,
            String score) {

        this(rule.getName(), Severity.valueOf(severity.name()), explanation, summary, solution, score);
    }
}
