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

package pbouda.jeffrey.common.analysis;

public interface AnalysisResult {

    enum Severity {
        OK(4, "#4abf02"),
        WARNING(1, "#e15a5a"),
        NA(3, "#383838"),
        INFO(2, "#03adfc"),
        IGNORE(5, "#cbccc8");

        private final int order;
        private final String color;

        Severity(int order, String color) {
            this.order = order;
            this.color = color;
        }

        public int order() {
            return order;
        }

        public String color() {
            return color;
        }
    }

    String rule();

    Severity severity();

    String explanation();

    String summary();

    String solution();

    String score();
}
