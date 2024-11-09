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

import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public abstract class JfrFileUtils {

    public static List<Path> listJfrFiles(Path directory) {
        try (var stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jfr"))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Cannot list JFR files: " + directory, e);
        }
    }

    public static Path findFirstJfrFile(Path recording) {
        try (Stream<Path> stream = Files.list(recording)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".jfr"))
                    .min(Comparator.naturalOrder())
                    .orElseThrow(() -> new IllegalArgumentException("Directory does not contain any JFR files: " + recording));
        } catch (IOException e) {
            throw new RuntimeException("Cannot find a directory: " + recording, e);
        }
    }

    public static void validJfrFile(Path recording,  boolean removeInvalid) {
        try (var rec = new RecordingFile(recording)) {
            rec.readEvent();
        } catch (IOException e) {
            if (removeInvalid) {
                FileSystemUtils.removeFile(recording);
            }
            throw new IllegalArgumentException("Invalid JFR file: " + recording, e);
        }
    }
}
