/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.parser.data;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoAnalysisDataProviderTest {

    private static final String RULE = "Exceptions Rule";

    @Nested
    class SummaryOrRule {

        @Test
        void keepsTheSummary() {
            assertEquals("Many exceptions thrown", AutoAnalysisDataProvider.summaryOrRule("Many exceptions thrown", RULE));
        }

        /** JMC returns verdicts with no summary; the rule name stands in so the result still reads. */
        @Test
        void fallsBackToTheRuleNameWhenTheSummaryIsMissing() {
            for (String summary : Arrays.asList(null, "", "  ")) {
                assertEquals(RULE, AutoAnalysisDataProvider.summaryOrRule(summary, RULE));
            }
        }
    }
}
