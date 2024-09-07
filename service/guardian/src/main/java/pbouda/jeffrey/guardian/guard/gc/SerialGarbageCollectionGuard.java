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

package pbouda.jeffrey.guardian.guard.gc;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.analysis.AnalysisResult;
import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.guardian.guard.MethodNameBasedGuard;
import pbouda.jeffrey.guardian.preconditions.Preconditions;

public class SerialGarbageCollectionGuard extends MethodNameBasedGuard {

    public SerialGarbageCollectionGuard(ProfileInfo profileInfo, double thresholdInPercent) {
        super("Serial GC Ratio",
                "VM_GenCollectForAllocation::doit",
                FrameType.CPP,
                profileInfo,
                thresholdInPercent);
    }

    @Override
    protected String summary(
            AnalysisResult.Severity severity,
            long totalSamples,
            long observedSamples,
            double ratioResult,
            double thresholdInPercent) {

        return "";
    }

    @Override
    protected String explanation() {
        return "";
    }

    @Override
    protected String solution(AnalysisResult.Severity severity) {
        return "";
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withEventSource(EventSource.ASYNC_PROFILER)
                .withGarbageCollectorType(GarbageCollectorType.SERIAL)
                .build();
    }
}
