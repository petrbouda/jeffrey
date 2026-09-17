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

package cafe.jeffrey.jib.payload;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;

import java.nio.file.Path;
import java.util.Optional;

/**
 * One payload file taken out of its jar, plus what the jar says about where it came from.
 *
 * @param file       the unpacked file on disk, at a path that is stable across builds
 * @param provenance the jar's {@value PayloadJars#PROVENANCE_ATTRIBUTE} manifest attribute — the
 *                   Jeffrey release or async-profiler version the binary was taken from — or empty
 *                   for a payload published without one
 */
public record UnpackedPayload(Path file, Optional<String> provenance) {

    private static final String PROVENANCE_OPEN = " (";
    private static final String PROVENANCE_CLOSE = ")";

    public UnpackedPayload {
        if (file == null) {
            throw new IllegalArgumentException("Unpacked payload file must not be null");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("Unpacked payload provenance must not be null");
        }
    }

    /** {@code /opt/jeffrey/provisioner (jeffrey v0.13.22)}, for the build log. */
    public String describe(AbsoluteUnixPath installPath) {
        return provenance
                .map(source -> installPath + PROVENANCE_OPEN + source + PROVENANCE_CLOSE)
                .orElse(installPath.toString());
    }
}
