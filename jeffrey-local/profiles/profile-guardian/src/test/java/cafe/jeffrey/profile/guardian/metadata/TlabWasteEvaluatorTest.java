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

package cafe.jeffrey.profile.guardian.metadata;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.GuardianProperties;
import cafe.jeffrey.profile.guardian.GuardianPropertiesTestDefaults;
import cafe.jeffrey.profile.guardian.GuardianResult;
import cafe.jeffrey.shared.common.model.EventSubtype;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlabWasteEvaluatorTest {

    private static final GuardianProperties PROPS = GuardianPropertiesTestDefaults.defaults();

    private static EventSummary summary(Type type, long weight) {
        return new EventSummary(type.code(), type.code(), RecordingEventSource.JDK, EventSubtype.EXECUTION_SAMPLE,
                0, weight, false, false, List.of(), Map.of(), Map.of());
    }

    @Test
    void noTlabEvents_returnsEmpty() {
        Optional<GuardianResult> result = TlabWasteEvaluator.evaluate(List.of(), PROPS);
        assertFalse(result.isPresent(), "When neither TLAB event is present the evaluator should produce no result");
    }

    @Test
    void mostlyInTlab_isOk() {
        // 5 MiB outside / 50 MiB total = 10% — at the info boundary (strict > comparison → still OK)
        List<EventSummary> summaries = List.of(
                summary(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 45 * 1024L * 1024),
                summary(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB, 5 * 1024L * 1024));

        GuardianResult result = TlabWasteEvaluator.evaluate(summaries, PROPS).orElseThrow();
        assertEquals(Severity.OK, result.analysisItem().severity());
    }

    @Test
    void aboveInfoBelowWarning_isInfo() {
        // 12 MiB / 100 MiB = 12% — above 10% info threshold, below 15% warning
        List<EventSummary> summaries = List.of(
                summary(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 88 * 1024L * 1024),
                summary(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB, 12 * 1024L * 1024));

        GuardianResult result = TlabWasteEvaluator.evaluate(summaries, PROPS).orElseThrow();
        assertEquals(Severity.INFO, result.analysisItem().severity());
    }

    @Test
    void aboveWarning_isWarning() {
        // 30 MiB / 100 MiB = 30% — well above 15% warning
        List<EventSummary> summaries = List.of(
                summary(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 70 * 1024L * 1024),
                summary(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB, 30 * 1024L * 1024));

        GuardianResult result = TlabWasteEvaluator.evaluate(summaries, PROPS).orElseThrow();
        assertEquals(Severity.WARNING, result.analysisItem().severity());
        assertTrue(result.analysisItem().score().startsWith("30.00"));
    }
}
