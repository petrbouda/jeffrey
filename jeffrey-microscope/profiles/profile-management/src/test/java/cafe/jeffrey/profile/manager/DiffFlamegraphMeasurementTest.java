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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiffFlamegraphMeasurementTest {

    @Test
    void equalSelectedWindowsHaveEqualExposureDespiteUnequalRecordings() {
        String result = manager(1000, 1000, 1000, 1000).rankedMovements(
                parameters(Type.EXECUTION_SAMPLE, false, new RelativeTimeRange(0, 10_000)), 10);
        assertTrue(result.contains("baseline_scale_factor: 1.000"), result);
        assertTrue(result.contains("primary_duration: PT10S"), result);
        assertTrue(result.contains("baseline_duration: PT10S"), result);
    }

    @Test
    void sampleModeUsesSamplesForAllocationRankingAndTree() {
        DiffFlamegraphManagerImpl manager = manager(500, 500, 9_000_000, 1_000_000);
        GraphParameters params = parameters(Type.OBJECT_ALLOCATION_SAMPLE, false, null);
        String result = manager.rankedMovements(params, 10);
        assertTrue(result.contains("primary_total: 500"), result);
        String tree = manager.generateAiExport(params);
        assertTrue(tree.contains("primary_total: 500"), tree);
    }

    @Test
    void methodTraceKeepsItsDefaultDurationMeasurement() {
        String result = manager(500, 500, 9_000_000, 1_000_000).rankedMovements(
                GraphParameters.builder().withEventType(Type.METHOD_TRACE).withUseWeight(null).build(), 10);
        assertTrue(result.contains("weight_unit: nanoseconds"), result);
        assertTrue(result.contains("measured_by: weight"), result);
    }

    @Test
    void largeByteTotalsDoNotHideTinySampleCounts() {
        String result = manager(3, 2, 9_000_000, 1_000_000).rankedMovements(
                parameters(Type.OBJECT_ALLOCATION_SAMPLE, true, null), 10);
        assertTrue(result.contains("only 3 measurements"), result);
        assertTrue(result.contains("only 2 measurements"), result);
    }

    private static GraphParameters parameters(Type type, boolean weighted, RelativeTimeRange range) {
        return GraphParameters.builder().withEventType(type).withUseWeight(weighted)
                .withTimeRange(range).build();
    }

    private static DiffFlamegraphManagerImpl manager(long primarySamples, long baselineSamples,
                                                    long primaryWeight, long baselineWeight) {
        Frame primary = frame(primarySamples, primaryWeight);
        Frame baseline = frame(baselineSamples, baselineWeight);
        DbBasedDiffgraphGenerator generator = mock(DbBasedDiffgraphGenerator.class);
        when(generator.diffFrame(any())).thenReturn(new DiffTreeGenerator(primary, baseline).generate());
        return new DiffFlamegraphManagerImpl(info("primary", 60), info("baseline", 120),
                null, null, generator, new AiExportConfig(0.1));
    }

    private static Frame frame(long samples, long weight) {
        Frame frame = new Frame(null, "work", 0, 0);
        frame.increment(FrameType.JIT_COMPILED, weight, samples, true);
        return frame;
    }

    private static ProfileInfo info(String id, long seconds) {
        return new ProfileInfo(id, null, null, id, RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(seconds), Instant.EPOCH,
                true, false, "recording-" + id);
    }
}
