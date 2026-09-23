/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Complete report for thread analysis in a heap dump including retained heap sizes.
 * The retained heap calculation is expensive, so results are pre-computed and stored.
 *
 * @param totalThreads      total Thread instances in the heap
 * @param daemonThreads     number of daemon threads
 * @param userThreads       number of user (non-daemon) threads
 * @param totalRetainedSize total retained heap size of all threads in bytes
 * @param threads           list of thread information with retained sizes
 */
public record ThreadAnalysisReport(
        int totalThreads,
        int daemonThreads,
        int userThreads,
        long totalRetainedSize,
        List<HeapThreadInfo> threads
) {
}
