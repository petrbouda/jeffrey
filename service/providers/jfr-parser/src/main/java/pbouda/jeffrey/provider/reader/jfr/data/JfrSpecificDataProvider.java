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

package pbouda.jeffrey.provider.reader.jfr.data;

import pbouda.jeffrey.provider.api.model.parser.RecordingTypeSpecificData;

import java.nio.file.Path;
import java.util.List;

public interface JfrSpecificDataProvider {

    /**
     * Generates data from the specific format of the source to be automatically cacheable.
     *
     * @param recordings files for parsing
     * @return JFR specific data parsed from the recordings
     */
    RecordingTypeSpecificData provide(List<Path> recordings);
}
