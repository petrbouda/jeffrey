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
