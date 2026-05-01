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

package cafe.jeffrey.profile.guardian;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalised Guardian thresholds, bound to the {@code jeffrey.microscope.guardian.*} property
 * namespace — matches the rest of the jeffrey-microscope configuration surface
 * ({@code jeffrey.microscope.ai.*}, {@code jeffrey.microscope.profile.*}, …).
 * <p>
 * Each threshold expresses a fraction (0..1) of the relevant metric:
 * <ul>
 *   <li>Execution/WallClock guards → fraction of total CPU samples</li>
 *   <li>Allocation guards → fraction of total allocated bytes</li>
 *   <li>Blocking guards → fraction of total blocking time</li>
 * </ul>
 * Each guard has two thresholds: {@code *InfoThreshold} (where severity flips to INFO) and
 * {@code *WarningThreshold} (where severity flips to WARNING). If both are equal, no INFO band is produced.
 * <p>
 * Minimum sample counts ({@code minSamples*}) gate whether a group runs at all; below the minimum
 * the group emits a Prerequisites NA instead.
 * <p>
 * All defaults are the same literals that were hard-coded in the original guard factories — chosen
 * empirically by the project author. Operators who want to tune sensitivity can override any field
 * via standard Spring property sources (env, CLI, {@code application.properties}).
 */
@ConfigurationProperties("jeffrey.microscope.guardian")
public record GuardianProperties(
        // ===== Execution Sample group =====
        @DefaultValue("1000")
        int minSamplesExecution,

        @DefaultValue("0.03")
        double logbackWarningThreshold,
        @DefaultValue("0.02")
        double logbackInfoThreshold,

        @DefaultValue("0.03")
        double log4jWarningThreshold,
        @DefaultValue("0.02")
        double log4jInfoThreshold,

        @DefaultValue("0.04")
        double hashMapCollisionWarningThreshold,
        @DefaultValue("0.02")
        double hashMapCollisionInfoThreshold,

        @DefaultValue("0.04")
        double regexWarningThreshold,
        @DefaultValue("0.02")
        double regexInfoThreshold,

        @DefaultValue("0.05")
        double reflectionWarningThreshold,
        @DefaultValue("0.03")
        double reflectionInfoThreshold,

        @DefaultValue("0.05")
        double classLoadingWarningThreshold,
        @DefaultValue("0.03")
        double classLoadingInfoThreshold,

        @DefaultValue("0.05")
        double serializationWarningThreshold,
        @DefaultValue("0.03")
        double serializationInfoThreshold,

        @DefaultValue("0.05")
        double xmlParsingWarningThreshold,
        @DefaultValue("0.03")
        double xmlParsingInfoThreshold,

        @DefaultValue("0.05")
        double jsonProcessingWarningThreshold,
        @DefaultValue("0.03")
        double jsonProcessingInfoThreshold,

        @DefaultValue("0.05")
        double exceptionWarningThreshold,
        @DefaultValue("0.03")
        double exceptionInfoThreshold,

        @DefaultValue("0.05")
        double stringConcatWarningThreshold,
        @DefaultValue("0.03")
        double stringConcatInfoThreshold,

        @DefaultValue("0.05")
        double threadSyncWarningThreshold,
        @DefaultValue("0.03")
        double threadSyncInfoThreshold,

        @DefaultValue("0.05")
        double cryptoWarningThreshold,
        @DefaultValue("0.03")
        double cryptoInfoThreshold,

        @DefaultValue("0.05")
        double compressWarningThreshold,
        @DefaultValue("0.03")
        double compressInfoThreshold,

        @DefaultValue("0.2")
        double jitCompilationWarningThreshold,
        @DefaultValue("0.15")
        double jitCompilationInfoThreshold,

        @DefaultValue("0.05")
        double deoptimizationWarningThreshold,
        @DefaultValue("0.03")
        double deoptimizationInfoThreshold,

        @DefaultValue("0.05")
        double safepointWarningThreshold,
        @DefaultValue("0.03")
        double safepointInfoThreshold,

        @DefaultValue("0.05")
        double vmOperationWarningThreshold,
        @DefaultValue("0.03")
        double vmOperationInfoThreshold,

        @DefaultValue("0.1")
        double gcWarningThreshold,
        @DefaultValue("0.07")
        double gcInfoThreshold,

        @DefaultValue("0.03")
        double finalizerCleanerWarningThreshold,
        @DefaultValue("0.01")
        double finalizerCleanerInfoThreshold,

        // ===== Allocation group =====
        @DefaultValue("1000")
        int minSamplesAllocation,

        @DefaultValue("0.1")
        double logbackAllocWarningThreshold,
        @DefaultValue("0.07")
        double logbackAllocInfoThreshold,

        @DefaultValue("0.05")
        double log4jAllocWarningThreshold,
        @DefaultValue("0.03")
        double log4jAllocInfoThreshold,

        @DefaultValue("0.05")
        double hashMapCollisionAllocWarningThreshold,
        @DefaultValue("0.03")
        double hashMapCollisionAllocInfoThreshold,

        @DefaultValue("0.05")
        double regexAllocWarningThreshold,
        @DefaultValue("0.03")
        double regexAllocInfoThreshold,

        @DefaultValue("0.05")
        double stringConcatAllocWarningThreshold,
        @DefaultValue("0.03")
        double stringConcatAllocInfoThreshold,

        @DefaultValue("0.05")
        double exceptionAllocWarningThreshold,
        @DefaultValue("0.03")
        double exceptionAllocInfoThreshold,

        @DefaultValue("0.05")
        double boxingAllocWarningThreshold,
        @DefaultValue("0.03")
        double boxingAllocInfoThreshold,

        @DefaultValue("0.05")
        double collectionAllocWarningThreshold,
        @DefaultValue("0.03")
        double collectionAllocInfoThreshold,

        @DefaultValue("0.15")
        double tlabWasteWarningThreshold,
        @DefaultValue("0.1")
        double tlabWasteInfoThreshold,

        // ===== Wall-Clock group =====
        @DefaultValue("1000")
        int minSamplesWallClock,

        // ===== Blocking group =====
        @DefaultValue("100")
        int minSamplesBlocking,

        @DefaultValue("0.05")
        double lockContentionWarningThreshold,
        @DefaultValue("0.03")
        double lockContentionInfoThreshold,

        @DefaultValue("0.05")
        double ioBlockingWarningThreshold,
        @DefaultValue("0.03")
        double ioBlockingInfoThreshold,

        @DefaultValue("0.05")
        double dbPoolBlockingWarningThreshold,
        @DefaultValue("0.03")
        double dbPoolBlockingInfoThreshold,

        @DefaultValue("0.05")
        double httpClientBlockingWarningThreshold,
        @DefaultValue("0.03")
        double httpClientBlockingInfoThreshold,

        @DefaultValue("0.05")
        double logbackBlockingWarningThreshold,
        @DefaultValue("0.03")
        double logbackBlockingInfoThreshold,

        @DefaultValue("0.05")
        double log4jBlockingWarningThreshold,
        @DefaultValue("0.03")
        double log4jBlockingInfoThreshold,

        // ===== Metadata / latency-tail guards =====
        // Per-event safepoint duration (ms) above which the Safepoint Outliers guard warns.
        @DefaultValue("100")
        long safepointOutlierWarningMillis,
        // INFO band lower bound (ms) for safepoint outliers.
        @DefaultValue("50")
        long safepointOutlierInfoMillis,

        // Per-event max pin duration (ms) above which Virtual Thread Pinning warns.
        @DefaultValue("20")
        long vthreadPinnedOutlierWarningMillis,
        // INFO band lower bound (ms) for virtual thread pinning outliers.
        @DefaultValue("10")
        long vthreadPinnedOutlierInfoMillis
) {
}
