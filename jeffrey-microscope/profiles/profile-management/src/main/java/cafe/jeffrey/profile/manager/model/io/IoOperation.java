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
 * A single I/O operation (one of the slowest), from a socket/file read/write event.
 *
 * @param kind          human label ("Socket Read", "File Write", …)
 * @param target        host:port for sockets, file path for files
 * @param bytes         bytes transferred
 * @param durationNanos operation duration
 * @param thread        the thread that performed the operation
 */
public record IoOperation(String kind, String target, long bytes, long durationNanos, String thread) {
}
