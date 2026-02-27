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

package pbouda.jeffrey.shared.common.model.repository;

import pbouda.jeffrey.shared.common.model.repository.matcher.AsprofCacheFileMatcher;
import pbouda.jeffrey.shared.common.model.repository.matcher.JvmLogFileMatcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;


public enum SupportedRecordingFile {
    // JFR_LZ4 must be before JFR to ensure .jfr.lz4 files are matched first
    JFR_LZ4(
            "LZ4 Compressed JDK Flight Recording",
            FileExtensions.JFR_LZ4,
            filename -> filename.endsWith("." + FileExtensions.JFR_LZ4),
            FileCategory.RECORDING
    ),
    JFR(
            "JDK Flight Recording",
            FileExtensions.JFR,
            filename -> filename.endsWith("." + FileExtensions.JFR),
            FileCategory.RECORDING
    ),
    ASPROF_TEMP(
            "Async Profiler Cache File",
            FileExtensions.ASPROF_TEMP,
            new AsprofCacheFileMatcher(),
            FileCategory.TEMPORARY
    ),
    HEAP_DUMP_GZ(
            "GZ Compressed  Heap Dump",
            FileExtensions.HPROF_GZ,
            filename -> filename.endsWith("." + FileExtensions.HPROF_GZ),
            FileCategory.ARTIFACT
    ),
    HEAP_DUMP(
            "Heap Dump",
            FileExtensions.HPROF,
            filename -> filename.endsWith("." + FileExtensions.HPROF),
            FileCategory.ARTIFACT
    ),
    PERF_COUNTERS(
            "HotSpot Performance Counters",
            FileExtensions.PERF_COUNTERS,
            filename -> filename.endsWith("." + FileExtensions.PERF_COUNTERS),
            FileCategory.ARTIFACT
    ),
    JVM_LOG(
            "JVM Log",
            FileExtensions.JVM_LOG,
            new JvmLogFileMatcher(),
            FileCategory.ARTIFACT
    ),
    HS_JVM_ERROR_LOG(
            "HotSpot JVM Error Log",
            FileExtensions.HS_JVM_ERROR_LOG,
            filename -> filename.endsWith(FileExtensions.HS_JVM_ERROR_LOG),
            FileCategory.ARTIFACT
    ),
    UNKNOWN(
            "Unsupported File Type",
            null,
            _ -> true,
            FileCategory.UNRECOGNIZED
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
    private final FileCategory fileCategory;

    SupportedRecordingFile(
            String description,
            String fileExtension,
            Predicate<String> filenameMatcher,
            FileCategory fileCategory) {

        this.description = description;
        this.fileExtension = fileExtension;
        this.filenameMatcher = filenameMatcher;
        this.fileCategory = fileCategory;
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

    public FileCategory fileCategory() {
        return fileCategory;
    }
}
