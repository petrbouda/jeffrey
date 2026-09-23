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

package cafe.jeffrey.shared.pendingindex;

import java.nio.file.Path;

/**
 * A single entry listed from a {@link PendingIndex}: the file it came from and the parsed
 * pointer it carries. The path is what {@link PendingIndex#remove} needs once the entry's
 * work is done.
 *
 * @param filePath the absolute path to the entry file
 * @param filename the filename (without directory) of the entry file
 * @param parsed   the parsed pointer
 * @param <T>      the type of the parsed pointer
 */
public record PendingIndexEntry<T>(
        Path filePath,
        String filename,
        T parsed) {
}
