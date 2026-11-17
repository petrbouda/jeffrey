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

package cafe.jeffrey.profile.manager.model.nmt;

/**
 * Native memory accounted to one NMT category ({@code jdk.NativeMemoryUsage.type}), e.g. "Java Heap",
 * "Class", "Thread", "Code", "GC". {@code reservedBytes} is address space; {@code committedBytes} is
 * actually backed by memory. {@code growthBytes} = last committed − first committed across the
 * recording, the leak signal (a steadily growing Thread or Class category points at a leak).
 *
 * @param category            NMT category name
 * @param reservedBytes       latest reserved bytes
 * @param committedBytes      latest committed bytes
 * @param startCommittedBytes committed bytes at the first sample
 * @param growthBytes         committed growth over the recording (may be negative)
 */
public record NmtCategory(
        String category,
        long reservedBytes,
        long committedBytes,
        long startCommittedBytes,
        long growthBytes) {
}
