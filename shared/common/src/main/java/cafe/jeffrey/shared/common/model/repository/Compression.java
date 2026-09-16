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
public interface Compression {

    /**
     * Whether a file of this type may be compressed at all. A caller that ignores this and calls
     * {@link #compress} anyway gets an exception rather than a quietly broken file.
     */
    boolean isSupported();

    /** Where the compressed form of this file belongs, beside the file itself. */
    Path target(Path source);

    /**
     * Writes the compressed form and returns where it landed. The source is left alone; removing
     * it once the result is verified is the caller's business, because only the caller knows
     * whether anything is still reading it.
     */
    Path compress(Path source, Path target);

    /** For every type whose compressed form nothing could classify. */
    Compression NONE = new NoCompression();

    /** LZ4, for recordings the rest of the tree can read compressed. */
    Compression LZ4 = new Lz4Compression();
}
