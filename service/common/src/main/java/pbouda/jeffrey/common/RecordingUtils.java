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

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class RecordingUtils {

    public static List<EventType> listEventTypes(Path recording) {
        try (var recFile = new RecordingFile(recording)) {
            return recFile.readEventTypes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}