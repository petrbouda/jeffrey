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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackSampleFlamegraphPanelProviderTest {

    private final StackSampleFlamegraphPanelProvider provider = new StackSampleFlamegraphPanelProvider();

    private static EventSummaryResult event(String code, String sampleType) {
        Map<String, String> extras = sampleType == null ? Map.of() : Map.of("sampleType", sampleType);
        EventSummaryResult.SingleResult primary =
                new EventSummaryResult.SingleResult(code, code, null, null, 10L, 0L, false, extras);
        return new EventSummaryResult(code, code, primary, null);
    }

    private FlamegraphPanel only(EventSummaryResult summary) {
        return provider.panels(List.of(summary), PanelContext.PRIMARY).getFirst();
    }

    @Test
    void titlesEachCardWithTheEventCodeVerbatim() {
        FlamegraphPanel panel = only(event("pprof.alloc_space", "alloc_space/bytes"));
        assertEquals("pprof.alloc_space", panel.title());
        assertEquals("pprof.alloc_space", panel.section());
        assertFalse(panel.showType());
        assertFalse(panel.threadMode().applicable());
    }

    @Test
    void byteUnitFormatsAsBytesAndIsOnByDefault() {
        FlamegraphPanel panel = only(event("otel.alloc", "alloc/bytes"));
        assertTrue(panel.weight().applicable());
        assertTrue(panel.weight().defaultOn());
        assertEquals(WeightKind.BYTES, panel.weight().kind());
    }

    @Test
    void nanosecondUnitFormatsAsDurationOffByDefault() {
        FlamegraphPanel panel = only(event("pprof.cpu", "cpu/nanoseconds"));
        assertTrue(panel.weight().applicable());
        assertFalse(panel.weight().defaultOn());
        assertEquals(WeightKind.DURATION, panel.weight().kind());
    }

    @Test
    void countUnitOffersNoWeightToggle() {
        assertFalse(only(event("pprof.samples", "samples/count")).weight().applicable());
        assertFalse(only(event("otel.samples", null)).weight().applicable());
    }
}
