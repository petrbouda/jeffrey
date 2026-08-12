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
