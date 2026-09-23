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

package cafe.jeffrey.profile.manager.model.io;

/**
 * Headline I/O metrics for one {@link IoKind} (socket or file) — the stream is scoped to that kind.
 *
 * @param bytesRead     total bytes read
 * @param bytesWritten  total bytes written
 * @param opCount       number of read + write operations
 * @param slowestNanos  duration of the slowest single operation
 * @param slowestTarget target (host:port or path) of that slowest operation
 * @param hasEvents     whether any event of this kind is present
 */
public record IoOverview(
        long bytesRead,
        long bytesWritten,
        long opCount,
        long slowestNanos,
        String slowestTarget,
        boolean hasEvents) {
}
