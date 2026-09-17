/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.project.repository;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The two kinds of file the hub does anything to: the JFR it compresses, and the archive that
 * compression produces. Every other file of a session — a log, a heap dump, the profiler's
 * scratch file — is one the hub lists and serves as it lies, and so has no constant here.
 *
 * <p>Deliberately not Microscope's {@code ManagedFile}. That enum knows a heap dump from a log
 * from a pprof profile because Microscope reads each differently; the hub reads none of them,
 * and a type it never acts on is a type it need not name. It forwards the file's name and
 * whether it is a recording, and Microscope classifies the name itself — so a kind of file
 * Microscope learns to read needs no change here.
 *
 * <p>Two things follow from being one of these. The <em>timestamp</em>: a JFR carries the
 * moment the profiler opened it in its own name, and only that survives the file being
 * rewritten, which is exactly why it is the one type that may be compressed. And the
 * <em>id</em>: a recording drops its extension so {@code profile-1.jfr} and the
 * {@code profile-1.jfr.lz4} the job turns it into are one id, and a reader holding the id from
 * before the rewrite still names the file after it. A file that is neither keeps its whole name
 * as its id and its filesystem time as its timestamp, because nothing renames it and stripping
 * would collide a {@code service.log} with a {@code service.hprof} beside it.
 */
public enum HubManagedFile {
    // JFR_LZ4 must be before JFR so that .jfr.lz4 is matched first
    JFR_LZ4(
            HubManagedFile.JFR_LZ4_EXTENSION,
            filename -> filename.endsWith("." + HubManagedFile.JFR_LZ4_EXTENSION),
            TimestampResolver.RECORDING_NAME
    ),
    JFR(
            HubManagedFile.JFR_EXTENSION,
            filename -> filename.endsWith("." + HubManagedFile.JFR_EXTENSION),
            TimestampResolver.RECORDING_NAME,
            Compression.LZ4
    );

    private static final String JFR_EXTENSION = "jfr";
    private static final String JFR_LZ4_EXTENSION = "jfr.lz4";

    private static final List<HubManagedFile> IN_MATCHING_ORDER = Arrays.asList(values());

    private final String fileExtension;
    private final Predicate<String> filenameMatcher;
    private final TimestampResolver timestampResolver;
    private final Compression compression;

    HubManagedFile(String fileExtension, Predicate<String> filenameMatcher, TimestampResolver timestampResolver) {
        this(fileExtension, filenameMatcher, timestampResolver, Compression.NONE);
    }

    HubManagedFile(
            String fileExtension,
            Predicate<String> filenameMatcher,
            TimestampResolver timestampResolver,
            Compression compression) {

        this.fileExtension = fileExtension;
        this.filenameMatcher = filenameMatcher;
        this.timestampResolver = timestampResolver;
        this.compression = compression;
    }

    public static Optional<HubManagedFile> of(Path path) {
        return of(path.getFileName().toString());
    }

    /**
     * The recording type of a file, or empty for a file the hub has no name for.
     */
    public static Optional<HubManagedFile> of(String filename) {
        return IN_MATCHING_ORDER.stream()
                .filter(type -> type.matches(filename))
                .findFirst();
    }

    /**
     * Whether a file of this name is of this type, ignoring the case of the name — a recording
     * copied off a case-preserving share is the same recording.
     */
    public boolean matches(String filename) {
        if (filename == null) {
            return false;
        }
        return filenameMatcher.test(filename.toLowerCase(Locale.ROOT));
    }

    /** A compressed form this hub wrote and closed in one pass — the far end of a rewrite. */
    public boolean isArchive() {
        return this == JFR_LZ4;
    }

    public TimestampResolver timestampResolver() {
        return timestampResolver;
    }

    public Compression compression() {
        return compression;
    }

    /**
     * The id a file of this type is known by within its session: its name with the extension
     * off, so that a recording and the archive it becomes are one id.
     */
    public String idOf(Path file) {
        String name = file.getFileName().toString();
        String suffix = "." + fileExtension;
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }
}
