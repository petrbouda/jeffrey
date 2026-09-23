/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.storage.recording.api.file;

import cafe.jeffrey.microscope.model.repository.FileExtensions;
import cafe.jeffrey.microscope.model.repository.matcher.AppLogFileMatcher;
import cafe.jeffrey.microscope.model.repository.matcher.AsprofCacheFileMatcher;
import cafe.jeffrey.microscope.model.repository.matcher.HsJvmErrorLogFileMatcher;
import cafe.jeffrey.microscope.model.repository.matcher.JvmLogFileMatcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Every kind of file Microscope knows how to read or attach: the recordings it parses, the
 * artifacts it keeps beside a profile, and the profiler's own scratch files it must not mistake
 * for either. Classified by name only.
 *
 * <p>Microscope's vocabulary, not the hub's. The hub decides what it does to a file — which it
 * compresses — with its own, much shorter enum, and forwards the rest by name; a file listed off
 * a hub is classified here again, from that name, so the two sides never have to agree on a type
 * they both spell.
 */
public enum ManagedFile {
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
    // Both crash-log spellings end in .log, so this must stay before APP_LOG
    HS_JVM_ERROR_LOG(
            "HotSpot JVM Error Log",
            FileExtensions.HS_JVM_ERROR_LOG,
            new HsJvmErrorLogFileMatcher(),
            FileCategory.ARTIFACT
    ),
    APP_LOG(
            "Application Log",
            FileExtensions.APP_LOG,
            new AppLogFileMatcher(),
            FileCategory.ARTIFACT
    ),
    PPROF(
            "pprof Profile",
            FileExtensions.PPROF,
            filename -> filename.endsWith("." + FileExtensions.PPROF)
                    || filename.endsWith("." + FileExtensions.PPROF_PB_GZ),
            FileCategory.RECORDING
    ),
    OTLP_PROFILE(
            "OpenTelemetry Profiles",
            FileExtensions.OTLP,
            filename -> filename.endsWith("." + FileExtensions.OTLP),
            FileCategory.RECORDING
    ),
    UNKNOWN(
            "Unsupported File Type",
            null,
            _ -> true,
            FileCategory.UNRECOGNIZED
    );

    private final static List<ManagedFile> KNOWN_TYPES;

    static {
        KNOWN_TYPES = Arrays.stream(values())
                .filter(file -> !(file == UNKNOWN))
                .toList();
    }

    private final String description;
    private final String fileExtension;
    private final Predicate<String> filenameMatcher;
    private final FileCategory fileCategory;

    ManagedFile(
            String description,
            String fileExtension,
            Predicate<String> filenameMatcher,
            FileCategory fileCategory) {

        this.description = description;
        this.fileExtension = fileExtension;
        this.filenameMatcher = filenameMatcher;
        this.fileCategory = fileCategory;
    }

    public static ManagedFile of(Path path) {
        return of(path.getFileName().toString());
    }

    public static ManagedFile of(String filename) {
        for (var managedFile : KNOWN_TYPES) {
            if (managedFile.matches(filename)) {
                return managedFile;
            }
        }
        return UNKNOWN;
    }

    public static ManagedFile ofType(String type) {
        for (var managedFile : KNOWN_TYPES) {
            if (managedFile.name().equals(type)) {
                return managedFile;
            }
        }
        return UNKNOWN;
    }

    /**
     * Whether a file of this name is of this type, ignoring the case of the name.
     *
     * <p>Normalised here rather than in {@link #of(String)} because this is also an entry point in its
     * own right — a caller asking one type directly, as the session-finished detector does — and a rule
     * that held for one door and not the other would be worse than either answer.
     *
     * <p>Case-insensitivity is about what a reader has on disk rather than about the formats: a heap
     * dump saved as {@code HEAP.HPROF}, a recording copied off a case-preserving share, or anything
     * that has been through a system that upper-cases names is the same file, and refusing it as an
     * unsupported type explains nothing to the person holding it.
     *
     * <p>{@link Locale#ROOT} rather than the default locale: under a Turkish locale
     * {@code toLowerCase()} maps {@code I} to a dotless {@code ı}, so a machine's language setting
     * would decide whether a file could be imported.
     */
    public boolean matches(String filename) {
        if (filename == null) {
            return false;
        }
        return filenameMatcher.test(filename.toLowerCase(Locale.ROOT));
    }

    public boolean matches(Path path) {
        return matches(path.getFileName().toString());
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
