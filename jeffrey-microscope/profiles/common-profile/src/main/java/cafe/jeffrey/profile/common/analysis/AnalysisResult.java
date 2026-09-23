/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.common.analysis;

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
