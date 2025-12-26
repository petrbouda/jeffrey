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

package pbouda.jeffrey.profile.manager.additional;

import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;
import java.util.Optional;

public interface AdditionalFileParser {

    /**
     * Parses the given file and returns an object representation of its content.
     *
     * @param filePath the path to the file to be parsed
     * @return an object representation of the file content
     */
    Optional<Object> parse(Path filePath);

    /**
     * Returns the supported recording file type for this parser.
     *
     * @return the supported recording file type
     */
    SupportedRecordingFile supportedRecordingFile();
}
