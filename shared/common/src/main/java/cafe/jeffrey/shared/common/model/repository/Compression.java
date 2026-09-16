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

package cafe.jeffrey.shared.common.model.repository;

import java.nio.file.Path;

/**
 * How a file of a given type is compressed, if it can be at all.
 *
 * <p>Compressing a file rewrites it under a new name, and a name is how everything downstream
 * decides what a file is: its {@link ManagedFile type}, the id it is known by, and —
 * for a recording — when it was opened. So a type may only be compressed when the compressed name
 * is one that still answers all three the same way. {@code profile-1.jfr} becomes
 * {@code profile-1.jfr.lz4}, which is a JFR_LZ4, still a recording, still the same id with the
 * extension stripped, and still carries its own timestamp. {@code app.pprof} would become
 * {@code app.pprof.lz4}, which matches nothing: UNKNOWN, no longer a recording, a different id,
 * and a creation time from the moment of compression. That file is lost to every reader of it and
 * the original has been deleted.
 *
 * <p>Hence {@link #NONE}, and hence it being the answer for every type but one. It is not a gap
 * waiting to be filled with an algorithm — it is the statement that rewriting this type destroys
 * it, and the caller is expected to leave the file alone.
 */
public sealed interface Compression permits Lz4Compression, NoCompression {

    /**
     * Whether a file of this type may be compressed at all. A caller that ignores this and calls
     * {@link #compress} anyway gets an exception rather than a quietly broken file.
     */
    boolean isSupported();

    /**
     * Whether this type's own name is one a compression produced — the far end of a rewrite
     * rather than the near one.
     *
     * <p>Declared here rather than read back out of the extension, because it is the same
     * question as {@link #isSupported()} asked from the other side and the pair has to agree: a
     * file is renamed by compression when it is either compressed or the result of compressing,
     * and those two are exactly the types whose id drops its extension. Derived from the name, it
     * answered for any type that happened to end in the compressed suffix, whether or not
     * anything here had written it.
     */
    boolean isArchive();

    /** Where the compressed form of this file belongs, beside the file itself. */
    Path target(Path source);

    /**
     * Writes the compressed form and returns where it landed.
     *
     * <p>The target appears whole or not at all: an implementation writes elsewhere and renames
     * onto the target, so no reader of the directory ever sees the name of a file still being
     * written into. A caller may therefore treat the target's existence as proof that the bytes
     * are all there — which is what makes it safe to delete the source on the strength of it,
     * here and on any later run that finds the archive already present.
     *
     * <p>The source is left alone; removing it is the caller's business, because only the caller
     * knows whether anything is still reading it.
     *
     * @throws RuntimeException when the compressed form could not be written, in which case the
     *                          target is left as it was and the source is untouched
     */
    Path compress(Path source, Path target);

    /** For every type whose compressed form nothing could classify. */
    Compression NONE = new NoCompression(false);

    /**
     * For a type that already is a compressed form. Nothing to compress either way, so it
     * refuses exactly as {@link #NONE} does; what it says in addition is that the name came from
     * a compression, which is what makes it strip to the same id as the recording it holds.
     */
    Compression ARCHIVE = new NoCompression(true);

    /** LZ4, for recordings the rest of the tree can read compressed. */
    Compression LZ4 = new Lz4Compression();
}
