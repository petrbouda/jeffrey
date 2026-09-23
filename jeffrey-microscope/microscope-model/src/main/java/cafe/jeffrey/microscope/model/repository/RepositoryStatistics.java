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

package cafe.jeffrey.microscope.model.repository;

/**
 * What a project's repository occupies on the hub.
 *
 * <p>One figure, because one figure is what anything actually read. This carried a status, a
 * session count, a file count, a last-activity timestamp, a biggest-session size and a count and
 * a size for each of six file-type buckets. The buckets were the last place anything on the hub
 * side decided what a file type <em>means</em>, and they decided wrong: pprof and OTLP recordings
 * landed in "other", beside the files nothing could classify at all. Of what remained, the status
 * had no reader anywhere, and the rest sat beside the same numbers the page already had.
 *
 * <p>Nothing here names a kind of file, so adding a kind of file changes neither
 * this record nor the RPC that carries it. A caller that wants to know what a particular file is
 * reads {@code fileType} off the session listing, where the hub reports it without acting on it.
 */
public record RepositoryStatistics(long totalSizeBytes) {
}
