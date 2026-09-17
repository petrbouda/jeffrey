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
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;

/**
 * One payload file to fetch and where to put it in the image.
 *
 * <p>Deliberately carries no entry name: a payload jar holds exactly one file under a fixed
 * prefix and {@link PayloadJars} finds it, so renaming a binary upstream cannot desynchronize
 * the extension from the payload it unpacks.
 *
 * @param coordinates the artifact to fetch
 * @param installPath where the file lands in the image
 * @param permissions the file mode; the native provisioner needs the executable bit, the rest
 *                    are only read
 */
public record PayloadRequest(
        ArtifactCoordinates coordinates, AbsoluteUnixPath installPath, FilePermissions permissions) {

    public PayloadRequest {
        if (coordinates == null) {
            throw new IllegalArgumentException("Payload coordinates must not be null");
        }
        if (installPath == null) {
            throw new IllegalArgumentException("Payload install path must not be null");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("Payload permissions must not be null");
        }
    }
}
