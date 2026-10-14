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
 * How a file of a given type is compressed.
 *
 * <p>A type may be compressed only when its compressed name keeps its type, id and timestamp:
 * {@code profile-1.jfr} → {@code profile-1.jfr.lz4} does; {@code app.pprof.lz4} would not, and the
 * original would be deleted. So {@link HubManagedFile#compression()} is empty for every type but JFR.
 */
public sealed interface Compression permits Lz4Compression {

    /** Where the compressed form of this file belongs, beside the file itself. */
    Path target(Path source);

    /**
     * Writes the compressed form and returns where it landed. The target appears whole or not at
     * all (written elsewhere, then renamed), so its existence proves the bytes are complete and the
     * caller may delete the source. The source itself is left alone.
     *
     * @throws RuntimeException when the compressed form could not be written, in which case the
     *                          target is left as it was and the source is untouched
     */
    Path compress(Path source, Path target);

    /** LZ4, for recordings the rest of the tree can read compressed. */
    Compression LZ4 = new Lz4Compression();
}
