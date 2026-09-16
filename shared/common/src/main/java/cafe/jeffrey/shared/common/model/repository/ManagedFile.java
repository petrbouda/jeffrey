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

import cafe.jeffrey.shared.common.model.repository.matcher.AppLogFileMatcher;
import cafe.jeffrey.shared.common.model.repository.matcher.AsprofCacheFileMatcher;
import cafe.jeffrey.shared.common.model.repository.matcher.HsJvmErrorLogFileMatcher;
import cafe.jeffrey.shared.common.model.repository.matcher.JvmLogFileMatcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;


public enum ManagedFile {
    // JFR_LZ4 must be before JFR to ensure .jfr.lz4 files are matched first
    JFR_LZ4(
            "LZ4 Compressed JDK Flight Recording",
            FileExtensions.JFR_LZ4,
            filename -> filename.endsWith("." + FileExtensions.JFR_LZ4),
            FileCategory.RECORDING,
            TimestampResolver.RECORDING_NAME,
            Compression.NONE
    ),
    JFR(
            "JDK Flight Recording",
            FileExtensions.JFR,
            filename -> filename.endsWith("." + FileExtensions.JFR),
            FileCategory.RECORDING,
            TimestampResolver.RECORDING_NAME,
            Compression.LZ4
    ),
    ASPROF_TEMP(
            "Async Profiler Cache File",
            FileExtensions.ASPROF_TEMP,
            new AsprofCacheFileMatcher(),
            FileCategory.TEMPORARY,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    HEAP_DUMP_GZ(
            "GZ Compressed  Heap Dump",
            FileExtensions.HPROF_GZ,
            filename -> filename.endsWith("." + FileExtensions.HPROF_GZ),
            FileCategory.ARTIFACT,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    HEAP_DUMP(
            "Heap Dump",
            FileExtensions.HPROF,
            filename -> filename.endsWith("." + FileExtensions.HPROF),
            FileCategory.ARTIFACT,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    PERF_COUNTERS(
            "HotSpot Performance Counters",
            FileExtensions.PERF_COUNTERS,
            filename -> filename.endsWith("." + FileExtensions.PERF_COUNTERS),
            FileCategory.ARTIFACT,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    JVM_LOG(
            "JVM Log",
            FileExtensions.JVM_LOG,
            new JvmLogFileMatcher(),
            FileCategory.ARTIFACT,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    // Both crash-log spellings end in .log, so this must stay before APP_LOG
    HS_JVM_ERROR_LOG(
            "HotSpot JVM Error Log",
            FileExtensions.HS_JVM_ERROR_LOG,
            new HsJvmErrorLogFileMatcher(),
            FileCategory.ARTIFACT,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    APP_LOG(
            "Application Log",
            FileExtensions.APP_LOG,
            new AppLogFileMatcher(),
            FileCategory.ARTIFACT,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    PPROF(
            "pprof Profile",
            FileExtensions.PPROF,
            filename -> filename.endsWith("." + FileExtensions.PPROF)
                    || filename.endsWith("." + FileExtensions.PPROF_PB_GZ),
            FileCategory.RECORDING,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    OTLP_PROFILE(
            "OpenTelemetry Profiles",
            FileExtensions.OTLP,
            filename -> filename.endsWith("." + FileExtensions.OTLP),
            FileCategory.RECORDING,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
    ),
    UNKNOWN(
            "Unsupported File Type",
            null,
            _ -> true,
            FileCategory.UNRECOGNIZED,
            TimestampResolver.FILESYSTEM,
            Compression.NONE
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
    private final TimestampResolver timestampResolver;
    private final Compression compression;

    ManagedFile(
            String description,
            String fileExtension,
            Predicate<String> filenameMatcher,
            FileCategory fileCategory,
            TimestampResolver timestampResolver,
            Compression compression) {

        this.description = description;
        this.fileExtension = fileExtension;
        this.filenameMatcher = filenameMatcher;
        this.fileCategory = fileCategory;
        this.timestampResolver = timestampResolver;
        this.compression = compression;
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

    /**
     * Where this type's timestamp comes from — its own name, or the filesystem.
     */
    public TimestampResolver timestampResolver() {
        return timestampResolver;
    }

    /**
     * How a file of this type is compressed, or {@link Compression#NONE} when rewriting it would
     * produce something no reader could place.
     */
    public Compression compression() {
        return compression;
    }

    /**
     * The id a file of this type is known by within its session.
     *
     * <p>Its own name, except for a type compression renames: there the extension comes off, so
     * {@code profile-1.jfr} and the {@code profile-1.jfr.lz4} the job turns it into are one id,
     * and a reader holding an id from before the rewrite still names the file after it. That is
     * the whole reason an id is not simply the name.
     *
     * <p>And the reason only those types drop it. Nothing renames a log or a heap dump, so
     * stripping their extension would buy nothing and would collide a {@code service.log} with a
     * {@code service.hprof} beside it. Several types spell their extension as a pattern rather
     * than a literal ({@code jvm-log(.[0-9]+)?}); none of them is renamed by compression, so none
     * reaches the stripping below.
     */
    public String idOf(Path file) {
        String name = file.getFileName().toString();
        if (!renamedByCompression()) {
            return name;
        }
        String suffix = "." + fileExtension;
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /**
     * Whether compression changes this type's name — because it compresses, or because it is
     * what a compression produced. The two ends of one rewrite, and the pair that has to answer
     * with the same id.
     */
    private boolean renamedByCompression() {
        return compression.isSupported() || isCompressed();
    }

    /**
     * Whether this type is itself a compressed form — a file written and closed in one pass by
     * whoever compressed it, so its bytes are final however old a listing of it is.
     */
    public boolean isCompressed() {
        return fileExtension != null && fileExtension.endsWith("." + FileExtensions.LZ4);
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
