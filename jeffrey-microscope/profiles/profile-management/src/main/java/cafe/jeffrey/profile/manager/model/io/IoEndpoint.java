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
 * Aggregated I/O for one endpoint — a socket peer ({@code host:port}) or a file path.
 *
 * @param target       host:port or file path
 * @param opCount      number of operations against this endpoint
 * @param bytes        total bytes transferred (read + written)
 * @param totalNanos   summed duration
 * @param maxNanos     slowest single operation against this endpoint
 */
public record IoEndpoint(String target, long opCount, long bytes, long totalNanos, long maxNanos) {
}
