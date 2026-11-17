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

package cafe.jeffrey.provider.profile.api;

import java.util.List;
import java.util.Map;

public interface ProfileToolsRepository {

    record FrameSample(String className, String methodName) {
    }

    record StacktraceRecord(long stacktraceHash, int typeId, long[] frameHashes, int[] tagIds) {
    }

    // --- Shared frame/stacktrace queries ---

    int countMatchingFrames(String classNamePattern);

    int countAffectedStacktraces(String classNamePattern);

    List<FrameSample> sampleMatchingFrames(String classNamePattern, int limit);

    List<Long> findMatchingFrameHashes(String classNamePattern);

    List<StacktraceRecord> findAffectedStacktraces(List<Long> matchingFrameHashes);

    // --- Stacktrace transformation ---

    void insertSyntheticFrame(long frameHash, String className);

    void applyStacktraceTransformation(Map<Long, Long> oldToNewHashMapping, List<StacktraceRecord> newStacktraces);

    void deleteEventsByStacktraces(List<Long> stacktraceHashes);

    // --- Orphan cleanup ---

    long deleteOrphanedStacktraces();

    long deleteOrphanedFrames();

    long deleteOrphanedThreads();

}
