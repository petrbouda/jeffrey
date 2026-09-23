/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.project.repository;

import java.nio.file.Path;

/**
 * How a file of a given type is compressed, if it can be at all.
 *
 * <p>Compressing a file rewrites it under a new name, and a name is how everything downstream
 * decides what a file is: its {@link HubManagedFile type}, the id it is known by, and —
 * for a recording — when it was opened. So a type may only be compressed when the compressed name
 * is one that still answers all three the same way. {@code profile-1.jfr} becomes
 * {@code profile-1.jfr.lz4}, which is a JFR_LZ4, still a recording, still the same id with the
 * extension stripped, and still carries its own timestamp. {@code app.pprof} would become
 * {@code app.pprof.lz4}, which matches nothing: UNKNOWN, no longer a recording, a different id,
 * and a creation time from the moment of compression. That file is lost to every reader of it and
 * the original has been deleted.
 *
 * <p>Hence {@link HubManagedFile#compression()} answering with an {@code Optional} that is empty
 * for every type but one. That emptiness is not a gap waiting to be filled with an algorithm —
 * it is the statement that rewriting this type destroys it, and the caller leaves the file alone.
 */
public sealed interface Compression permits Lz4Compression {

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

    /** LZ4, for recordings the rest of the tree can read compressed. */
    Compression LZ4 = new Lz4Compression();
}
