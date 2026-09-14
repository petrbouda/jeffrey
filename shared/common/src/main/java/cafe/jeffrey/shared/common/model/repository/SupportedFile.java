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

package cafe.jeffrey.shared.common.model.repository;

import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.matcher.AppLogFileMatcher;
import cafe.jeffrey.shared.common.model.repository.matcher.AsprofCacheFileMatcher;
import cafe.jeffrey.shared.common.model.repository.matcher.HsJvmErrorLogFileMatcher;
import cafe.jeffrey.shared.common.model.repository.matcher.JvmLogFileMatcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Every file type Jeffrey knows, and everything Jeffrey knows about it.
 *
 * <p>This is the single registry of per-type behaviour: how a file of the type is recognised on
 * disk, whether it is a chunk of a session's recording, whether the hub itself wrote it, whether Microscope parses it as the recording a profile is
 * built from, which statistics bucket it lands in. Nothing else in the code base carries a
 * per-type switch; a place that needs to treat one type differently asks the type.
 *
 * <p>Files that no declared type recognises are {@link #UNKNOWN}: the fallback rather than a
 * matcher, with nothing set. Such a file is served and downloaded like any other; it is only
 * never a chunk and never a profile.
 *
 * <p>Declaration order is matching order — {@link #JFR_LZ4} before {@link #JFR} so that
 * {@code .jfr.lz4} is not taken for a raw chunk, {@link #HS_JVM_ERROR_LOG} before {@link #APP_LOG}
 * because both crash-log spellings end in {@code .log}.
 */
public enum SupportedFile {

    JFR_LZ4(spec("LZ4 Compressed JDK Flight Recording", FileExtensions.JFR_LZ4)
            .matching(endsWith(FileExtensions.JFR_LZ4))
            .recordingChunk()
            .compressedByHub()
            .profileRecording()
            .stats(StatsCategory.JFR)),

    JFR(spec("JDK Flight Recording", FileExtensions.JFR)
            .matching(endsWith(FileExtensions.JFR))
            .recordingChunk()
            .profileRecording()
            .stats(StatsCategory.JFR)),

    ASPROF_TEMP(spec("Async Profiler Cache File", FileExtensions.ASPROF_TEMP)
            .matching(new AsprofCacheFileMatcher())
            .transientFile()),

    HEAP_DUMP_GZ(spec("GZ Compressed Heap Dump", FileExtensions.HPROF_GZ)
            .matching(endsWith(FileExtensions.HPROF_GZ))
            .eventSource(RecordingEventSource.HEAP_DUMP)
            .stats(StatsCategory.HEAP_DUMP)),

    HEAP_DUMP(spec("Heap Dump", FileExtensions.HPROF)
            .matching(endsWith(FileExtensions.HPROF))
            .eventSource(RecordingEventSource.HEAP_DUMP)
            .stats(StatsCategory.HEAP_DUMP)),

    PERF_COUNTERS(spec("HotSpot Performance Counters", FileExtensions.PERF_COUNTERS)
            .matching(endsWith(FileExtensions.PERF_COUNTERS))),

    JVM_LOG(spec("JVM Log", FileExtensions.JVM_LOG)
            .matching(new JvmLogFileMatcher())
            .stats(StatsCategory.LOG)),

    HS_JVM_ERROR_LOG(spec("HotSpot JVM Error Log", FileExtensions.HS_JVM_ERROR_LOG)
            .matching(new HsJvmErrorLogFileMatcher())
            .crashSignal()
            .stats(StatsCategory.ERROR_LOG)),

    APP_LOG(spec("Application Log", FileExtensions.APP_LOG)
            .matching(new AppLogFileMatcher())
            .stats(StatsCategory.APP_LOG)),

    PPROF(spec("pprof Profile", FileExtensions.PPROF)
            .matching(endsWith(FileExtensions.PPROF).or(endsWith(FileExtensions.PPROF_PB_GZ)))
            .eventSource(RecordingEventSource.PPROF)
            .profileRecording()),

    OTLP_PROFILE(spec("OpenTelemetry Profiles", FileExtensions.OTLP)
            .matching(endsWith(FileExtensions.OTLP))
            .eventSource(RecordingEventSource.OPEN_TELEMETRY)
            .profileRecording()),

    UNKNOWN(fallback("Unsupported File Type"));

    private static final String EXTENSION_SEPARATOR = ".";

    private static final List<SupportedFile> MATCHABLE = Arrays.stream(values())
            .filter(file -> file.matcher != null)
            .toList();

    private static final List<String> RECORDING_CHUNK_EXTENSIONS = Arrays.stream(values())
            .filter(SupportedFile::isRecordingChunk)
            .map(SupportedFile::fileExtension)
            .toList();

    private static final List<SupportedFile> PROFILE_RECORDINGS = Arrays.stream(values())
            .filter(SupportedFile::isProfileRecording)
            .toList();

    private static final Map<String, SupportedFile> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    private final String description;
    private final String fileExtension;
    private final Predicate<String> matcher;
    private final boolean recordingChunk;
    private final boolean transientFile;
    private final boolean compressedByHub;
    private final boolean crashSignal;
    private final boolean profileRecording;
    private final RecordingEventSource eventSource;
    private final StatsCategory statsCategory;

    SupportedFile(Spec spec) {
        spec.validate();
        this.description = spec.description;
        this.fileExtension = spec.fileExtension;
        this.matcher = spec.matcher;
        this.recordingChunk = spec.recordingChunk;
        this.transientFile = spec.transientFile;
        this.compressedByHub = spec.compressedByHub;
        this.crashSignal = spec.crashSignal;
        this.profileRecording = spec.profileRecording;
        this.eventSource = spec.eventSource;
        this.statsCategory = spec.statsCategory;
    }

    // ========== Lookup ==========

    public static SupportedFile of(Path path) {
        return of(path.getFileName().toString());
    }

    /**
     * The first declared type whose matcher accepts the name, or {@link #UNKNOWN}.
     */
    public static SupportedFile of(String filename) {
        for (SupportedFile file : MATCHABLE) {
            if (file.matches(filename)) {
                return file;
            }
        }
        return UNKNOWN;
    }

    /**
     * The type of the given name, or {@link #UNKNOWN} for a name no type carries — the wire
     * sends the name, and an unknown one must not surface as an exception.
     */
    public static SupportedFile ofType(String type) {
        return type == null ? UNKNOWN : BY_NAME.getOrDefault(type, UNKNOWN);
    }

    /**
     * Whether a file of this name is of this type, ignoring the case of the name. Always false
     * for {@link #UNKNOWN}, which recognises nothing and is what {@link #of} answers instead.
     *
     * <p>Normalised here rather than in {@link #of(String)} because this is also an entry point in its
     * own right — a caller asking one type directly — and a rule that held for one door and not the
     * other would be worse than either answer.
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
        if (filename == null || matcher == null) {
            return false;
        }
        return matcher.test(filename.toLowerCase(Locale.ROOT));
    }

    public boolean matches(Path path) {
        return matches(path.getFileName().toString());
    }

    // ========== Per-type facts ==========

    public String description() {
        return description;
    }

    public String fileExtension() {
        return fileExtension;
    }

    /**
     * A chunk of a session's recording: one of the rotated files the profiler writes, which the hub
     * compresses, trims by age and replays, and which Microscope assembles into the recording a
     * profile is built from. The newest chunk of a running session is the one still being written.
     */
    public boolean isRecordingChunk() {
        return recordingChunk;
    }

    /**
     * A file the producer leaves behind while working and takes away again; listed so a reader
     * sees it exists, never served.
     */
    public boolean isTransient() {
        return transientFile;
    }

    /**
     * Written and closed by the hub itself, so its size can be read from the directory listing
     * even while the session is running.
     */
    public boolean isCompressedByHub() {
        return compressedByHub;
    }

    /**
     * Its presence in a finished session means the JVM crashed.
     */
    public boolean isCrashSignal() {
        return crashSignal;
    }

    /**
     * A file Microscope parses as the recording a profile is built from, and therefore the one
     * file a recording folder holds beside its additional files.
     */
    public boolean isProfileRecording() {
        return profileRecording;
    }

    /**
     * The kind of events a file of this type holds, when the name alone says so. A JFR file is
     * empty here: whether async-profiler or the JDK wrote it is read from its content.
     */
    public Optional<RecordingEventSource> eventSource() {
        return Optional.ofNullable(eventSource);
    }

    public StatsCategory statsCategory() {
        return statsCategory;
    }

    // ========== Across types ==========

    /**
     * The extensions of every recording-chunk type, in declaration order, so that a compound
     * extension ({@code jfr.lz4}) is tried before the one it ends with ({@code jfr}).
     */
    public static List<String> recordingChunkExtensions() {
        return RECORDING_CHUNK_EXTENSIONS;
    }

    /**
     * The types Microscope parses as a profile's recording, in the order a recording folder is
     * searched for one.
     */
    public static List<SupportedFile> profileRecordings() {
        return PROFILE_RECORDINGS;
    }

    // ========== Declaration ==========

    private static Spec spec(String description, String fileExtension) {
        return new Spec(description, fileExtension);
    }

    private static Spec fallback(String description) {
        return new Spec(description, null);
    }

    private static Predicate<String> endsWith(String extension) {
        String suffix = EXTENSION_SEPARATOR + extension;
        return filename -> filename.endsWith(suffix);
    }

    /**
     * What one constant declares about itself; read once by the constructor.
     */
    private static final class Spec {
        private final String description;
        private final String fileExtension;
        private Predicate<String> matcher;
        private boolean recordingChunk;
        private boolean transientFile;
        private boolean compressedByHub;
        private boolean crashSignal;
        private boolean profileRecording;
        private RecordingEventSource eventSource;
        private StatsCategory statsCategory = StatsCategory.OTHER;

        private Spec(String description, String fileExtension) {
            this.description = Objects.requireNonNull(description);
            this.fileExtension = fileExtension;
        }

        private Spec matching(Predicate<String> matcher) {
            this.matcher = Objects.requireNonNull(matcher);
            return this;
        }

        private Spec recordingChunk() {
            this.recordingChunk = true;
            return this;
        }

        /**
         * Excludes every other capability: a file that is never served cannot be replayed
         * or parsed either, and declaring both would be a contradiction the constant cannot honour.
         * Checked once the whole declaration is in, so that the order the traits were written in
         * cannot slip one past the rule.
         */
        private Spec transientFile() {
            this.transientFile = true;
            return this;
        }

        private void validate() {
            if (transientFile && (recordingChunk || profileRecording || eventSource != null || compressedByHub)) {
                throw new IllegalStateException("A transient file declares no other capability: " + description);
            }
        }

        private Spec compressedByHub() {
            this.compressedByHub = true;
            return this;
        }

        private Spec crashSignal() {
            this.crashSignal = true;
            return this;
        }

        private Spec profileRecording() {
            this.profileRecording = true;
            return this;
        }

        private Spec eventSource(RecordingEventSource eventSource) {
            this.eventSource = Objects.requireNonNull(eventSource);
            return this;
        }

        private Spec stats(StatsCategory statsCategory) {
            this.statsCategory = Objects.requireNonNull(statsCategory);
            return this;
        }
    }
}
