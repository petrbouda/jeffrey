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

package pbouda.jeffrey.shared.folderqueue;

import java.nio.file.Path;

/**
 * A single entry polled from the folder queue, containing the original
 * file path, its filename, and the parsed value produced by the
 * {@link FolderQueueEntryParser}.
 *
 * @param filePath the absolute path to the event file
 * @param filename the filename (without directory) of the event file
 * @param parsed   the parsed value
 * @param <T>      the type of the parsed value
 */
public record FolderQueueEntry<T>(
        Path filePath,
        String filename,
        T parsed) {
}
