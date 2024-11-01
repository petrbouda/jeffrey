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

package pbouda.jeffrey.common.filesystem;

import pbouda.jeffrey.common.Recording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class RecordingUtils {

    public static List<Recording> all(Path recordingsDir) {
        try (Stream<Path> recordings = Files.walk(recordingsDir)) {
            return recordings
                    .filter(hasJfrSuffix())
                    .map(file -> toRecording(recordingsDir, file))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Cannot iterate over the recordings: recordings_dir=" + recordingsDir, e);
        }
    }

    private static Predicate<Path> hasJfrSuffix() {
        return f -> f.getFileName().toString().endsWith(".jfr");
    }

    private static Recording toRecording(Path directory, Path file) {
        try {
            Instant modificationTime = Files.getLastModifiedTime(file).toInstant();

            return new Recording(
                    directory.relativize(file),
                    directory.resolve(file),
                    toDateTime(modificationTime),
                    Files.size(file));
        } catch (IOException e) {
            throw new RuntimeException("Cannot get info about profile: " + file, e);
        }
    }

    private static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }
}
