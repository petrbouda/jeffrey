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

package pbouda.jeffrey.model;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public enum SupportedRecordingFile {
    JFR("JDK Flight Recording", filename -> filename.endsWith(".jfr")),
    HEAP_DUMP("Heap Dump", filename -> filename.endsWith(".hprof")),
    PERF_COUNTERS("HotSpot Performance Counters", filename -> filename.endsWith(".hsperfdata")),
    UNKNOWN("Unsupported File Type", _ -> true);

    private final static List<SupportedRecordingFile> KNOWN_TYPES;

    static {
        KNOWN_TYPES = Arrays.stream(values())
                .filter(file -> !(file == UNKNOWN))
                .toList();
    }

    private final String description;
    private final Predicate<String> filenameMatcher;

    SupportedRecordingFile(String description, Predicate<String> filenameMatcher) {
        this.description = description;
        this.filenameMatcher = filenameMatcher;
    }

    public static SupportedRecordingFile of(String filename) {
        for (var supportedRecordingFile : KNOWN_TYPES) {
            if (supportedRecordingFile.matches(filename)) {
                return supportedRecordingFile;
            }
        }
        return UNKNOWN;
    }

    public boolean matches(String filename) {
        return filenameMatcher.test(filename);
    }

    public String description() {
        return description;
    }
}
