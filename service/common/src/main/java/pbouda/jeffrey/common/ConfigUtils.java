/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.util.List;

public abstract class ConfigUtils {

    public static List<Path> resolveRecordings(Path recording, Path recordingDir) {
        if (recording != null && recordingDir!= null) {
            throw new IllegalArgumentException(
                    "Only one of the 'recording' or 'recordingDir' can be specified");
        } else if (recording == null && recordingDir== null) {
            throw new IllegalArgumentException(
                    "One of the 'recording' or 'recordingDir' can be specified");
        } else if (recording != null) {
            return List.of(recording);
        } else {
            return FileUtils.listJfrFiles(recordingDir);
        }
    }
}
