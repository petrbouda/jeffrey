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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

import cafe.jeffrey.profile.manager.heapdump.HeapDumpStages;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One report {@code heap_prepare} can compute on its own, named on the wire by its constant — the
 * spelling an answer lists it in and the input takes back. The pipeline's own stage id, which the web
 * UI shares, stays behind {@link #stageId()}.
 */
public enum HeapReport {

    STRINGS(HeapDumpStages.STRINGS),
    DOMINATOR(HeapDumpStages.DOMINATOR),
    THREADS(HeapDumpStages.THREADS),
    BIGGEST(HeapDumpStages.BIGGEST),
    COLLECTIONS(HeapDumpStages.COLLECTIONS),
    LEAKS(HeapDumpStages.LEAKS),
    CLASSLOADERS(HeapDumpStages.CLASSLOADERS),
    BIGGEST_COLLECTIONS(HeapDumpStages.BIGGEST_COLLECTIONS),
    CONSUMERS(HeapDumpStages.CONSUMERS),
    DUPLICATES(HeapDumpStages.DUPLICATES);

    private static final Map<String, HeapReport> BY_STAGE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(HeapReport::stageId, Function.identity()));

    private final String stageId;

    HeapReport(String stageId) {
        this.stageId = stageId;
    }

    /** The pipeline stage that computes this report. */
    public String stageId() {
        return stageId;
    }

    /**
     * @throws IllegalArgumentException for a stage that computes no report, such as the index build
     */
    public static HeapReport ofStage(String stageId) {
        HeapReport report = BY_STAGE.get(stageId);
        if (report == null) {
            throw new IllegalArgumentException("Not a heap report stage: " + stageId);
        }
        return report;
    }

    /** Whether the stage computes a report, rather than building the index. */
    public static boolean isReport(String stageId) {
        return BY_STAGE.containsKey(stageId);
    }
}
