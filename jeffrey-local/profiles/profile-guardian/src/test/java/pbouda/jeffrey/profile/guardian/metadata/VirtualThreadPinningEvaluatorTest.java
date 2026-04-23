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

package pbouda.jeffrey.profile.guardian.metadata;

import org.junit.jupiter.api.Test;
import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.profile.guardian.GuardianProperties;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.provider.profile.model.EventDurationStats;
import pbouda.jeffrey.shared.common.model.Type;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VirtualThreadPinningEvaluatorTest {

    private static final GuardianProperties PROPS = GuardianProperties.defaults();

    private static EventDurationStats stats(long count, long p99Ms, long maxMs, long totalMs) {
        return new EventDurationStats(count, totalMs * 1_000_000L, maxMs * 1_000_000L, p99Ms * 1_000_000L);
    }

    @Test
    void noPinEvents_returnsEmpty() {
        Optional<GuardianResult> result = VirtualThreadPinningEvaluator.evaluate(new StubEventRepository(), PROPS);
        assertFalse(result.isPresent());
    }

    @Test
    void shortPins_isOk() {
        // max = 5 ms (below info=10ms)
        StubEventRepository repo = new StubEventRepository()
                .put(Type.VIRTUAL_THREAD_PINNED, stats(100, 4, 5, 200));
        GuardianResult result = VirtualThreadPinningEvaluator.evaluate(repo, PROPS).orElseThrow();
        assertEquals(Severity.OK, result.analysisItem().severity());
    }

    @Test
    void maxBetweenThresholds_isInfo() {
        // max = 15 ms (between info=10 and warn=20)
        StubEventRepository repo = new StubEventRepository()
                .put(Type.VIRTUAL_THREAD_PINNED, stats(100, 8, 15, 500));
        GuardianResult result = VirtualThreadPinningEvaluator.evaluate(repo, PROPS).orElseThrow();
        assertEquals(Severity.INFO, result.analysisItem().severity());
    }

    @Test
    void maxAboveWarning_isWarning() {
        // max = 50 ms
        StubEventRepository repo = new StubEventRepository()
                .put(Type.VIRTUAL_THREAD_PINNED, stats(100, 12, 50, 800));
        GuardianResult result = VirtualThreadPinningEvaluator.evaluate(repo, PROPS).orElseThrow();
        assertEquals(Severity.WARNING, result.analysisItem().severity());
    }
}
