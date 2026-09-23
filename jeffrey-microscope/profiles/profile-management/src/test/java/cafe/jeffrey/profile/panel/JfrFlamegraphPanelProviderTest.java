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

package cafe.jeffrey.profile.panel;

import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.model.WeightKind;
import cafe.jeffrey.shared.common.model.EventTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JfrFlamegraphPanelProviderTest {

    private final JfrFlamegraphPanelProvider provider = new JfrFlamegraphPanelProvider();

    private static EventSummaryResult summary(String code, long samples) {
        EventSummaryResult.SingleResult primary =
                new EventSummaryResult.SingleResult(code, code, null, null, samples, 0L, false, Map.of());
        return new EventSummaryResult(code, code, primary, null);
    }

    private static FlamegraphPanel bySection(List<FlamegraphPanel> panels, String section) {
        return panels.stream().filter(p -> p.section().equals(section)).findFirst().orElseThrow();
    }

    @Test
    void emitsAllEightSectionsInOrderWithPlaceholdersForEmptyOnes() {
        List<FlamegraphPanel> panels = provider.panels(
                List.of(summary(EventTypeName.EXECUTION_SAMPLE, 42)), PanelContext.PRIMARY);

        List<String> sections = panels.stream().map(FlamegraphPanel::section).toList();
        assertEquals(List.of("execution", "cpu-time", "method", "wall",
                "allocation", "native-alloc", "native-leak", "blocking"), sections);

        // real execution panel carries the samples; every other section is a zero-sample placeholder
        assertEquals(42, bySection(panels, "execution").event().primary().samples());
        assertEquals(0, bySection(panels, "cpu-time").event().primary().samples());
        assertEquals(0, bySection(panels, "allocation").event().primary().samples());
    }

    @Test
    void executionPanelPresentationMatchesTemplate() {
        FlamegraphPanel exec = bySection(
                provider.panels(List.of(summary(EventTypeName.EXECUTION_SAMPLE, 1)), PanelContext.PRIMARY),
                "execution");

        assertEquals("Execution Samples", exec.title());
        assertEquals("blue", exec.color());
        assertTrue(exec.showType());
        assertFalse(exec.weight().applicable());
    }

    @Test
    void allocationPanelUsesBytesWeightOnByDefault() {
        FlamegraphPanel alloc = bySection(
                provider.panels(List.of(summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 5)), PanelContext.PRIMARY),
                "allocation");

        assertEquals("Allocation Samples", alloc.title());
        assertTrue(alloc.weight().applicable());
        assertTrue(alloc.weight().defaultOn());
        assertEquals(WeightKind.BYTES, alloc.weight().kind());
    }

    @Test
    void blockingPanelTitledFromEventLabel() {
        EventSummaryResult monitor = summary(EventTypeName.JAVA_MONITOR_ENTER, 7);
        FlamegraphPanel blocking = bySection(
                provider.panels(List.of(monitor), PanelContext.PRIMARY), "blocking");

        assertEquals(monitor.label(), blocking.title());
        assertTrue(blocking.classification().blocking());
    }

    @Test
    void methodWeightAndThreadModeAreOfferedInPrimaryButNotDifferential() {
        FlamegraphPanel primary = bySection(
                provider.panels(List.of(summary(EventTypeName.METHOD_TRACE, 3)), PanelContext.PRIMARY), "method");
        assertTrue(primary.weight().applicable());
        assertTrue(primary.threadMode().applicable());

        FlamegraphPanel differential = bySection(
                provider.panels(List.of(summary(EventTypeName.METHOD_TRACE, 3)), PanelContext.DIFFERENTIAL), "method");
        assertFalse(differential.weight().applicable());
        assertFalse(differential.threadMode().applicable());
        assertTrue(differential.classification().method());
    }
}
