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
        OK(5), WARNING(1), NA(3), INFO(2), IGNORE(4);

        private final int order;

        Severity(int order) {
            this.order = order;
        }

        public int order() {
            return order;
        }
    }

    String rule();

    Severity severity();

    String explanation();

    String summary();

    String solution();

    String score();
}
