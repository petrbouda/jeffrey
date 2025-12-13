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

package pbouda.jeffrey.common.model.repository;

import pbouda.jeffrey.common.model.repository.matcher.AsprofCacheFileMatcher;
import pbouda.jeffrey.common.model.repository.matcher.JvmLogFileMatcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public enum SupportedRecordingFile {
    JFR(
            "JDK Flight Recording",
            FileExtensions.JFR,
            filename -> filename.endsWith("." + FileExtensions.JFR),
            false
    ),
    ASPROF_TEMP(
            "Async Profiler Cache ",
            FileExtensions.ASPROF_TEMP,
            new AsprofCacheFileMatcher(),
            false
    ),
    HEAP_DUMP(
            "Heap Dump",
            FileExtensions.HPROF,
            filename -> filename.endsWith("." + FileExtensions.HPROF),
            true
    ),
    PERF_COUNTERS(
            "HotSpot Performance Counters",
            FileExtensions.PERF_COUNTERS,
            filename -> filename.endsWith("." + FileExtensions.PERF_COUNTERS),
            true
    ),
    JVM_LOG(
            "JVM Log",
            FileExtensions.JVM_LOG,
            new JvmLogFileMatcher(),
            true
    ),
    UNKNOWN(
            "Unsupported File Type",
            null,
            _ -> true,
            true
    );

    private final static List<SupportedRecordingFile> KNOWN_TYPES;

    static {
        KNOWN_TYPES = Arrays.stream(values())
                .filter(file -> !(file == UNKNOWN))
                .toList();
    }

    private final String description;
    private final String fileExtension;
    private final Predicate<String> filenameMatcher;
    private final boolean isAdditionalFile;

    SupportedRecordingFile(
            String description,
            String fileExtension,
            Predicate<String> filenameMatcher,
            boolean isAdditionalFile) {

        this.description = description;
        this.fileExtension = fileExtension;
        this.filenameMatcher = filenameMatcher;
        this.isAdditionalFile = isAdditionalFile;
    }

    public static SupportedRecordingFile of(Path path) {
        return of(path.getFileName().toString());
    }

    public static SupportedRecordingFile of(String filename) {
        for (var supportedRecordingFile : KNOWN_TYPES) {
            if (supportedRecordingFile.matches(filename)) {
                return supportedRecordingFile;
            }
        }
        return UNKNOWN;
    }

    public static SupportedRecordingFile ofType(String type) {
        for (var supportedRecordingFile : KNOWN_TYPES) {
            if (supportedRecordingFile.name().equals(type)) {
                return supportedRecordingFile;
            }
        }
        return UNKNOWN;
    }

    public boolean matches(String filename) {
        return filenameMatcher.test(filename);
    }

    public boolean matches(Path path) {
        return matches(path.getFileName().toString());
    }

    public String appendExtension(String filename) {
        return fileExtension != null ? filename + "." + fileExtension : null;
    }

    public String fileExtension() {
        return fileExtension;
    }

    public String description() {
        return description;
    }

    public boolean isAdditionalFile() {
        return isAdditionalFile;
    }
}
