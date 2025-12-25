/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.project.repository.file;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

public interface FileInfoProcessor {

    /**
     * Returns a comparator that can be used to sort files in the folder.
     *
     * @return a comparator for sorting files
     */
    Comparator<Path> comparator();

    /**
     * Returns the instant when the file was created.
     *
     * @param filePath the path to the file
     * @return the creation time of the file
     */
    Instant createdAt(Path filePath);
}
