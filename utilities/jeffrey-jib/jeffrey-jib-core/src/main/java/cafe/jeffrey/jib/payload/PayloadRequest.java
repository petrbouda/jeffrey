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
 * One payload file to take from the payload jar and where to put it in the image.
 *
 * @param resource    the class-path resource inside the payload jar, e.g.
 *                    {@code jeffrey-payload/provisioner-linux-amd64}
 * @param installPath where the file lands in the image
 * @param permissions the file mode; the native provisioner needs the executable bit, the rest
 *                    are only read
 */
public record PayloadRequest(String resource, AbsoluteUnixPath installPath, FilePermissions permissions) {

    public PayloadRequest {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Payload resource must not be blank");
        }
        if (installPath == null) {
            throw new IllegalArgumentException("Payload install path must not be null");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("Payload permissions must not be null");
        }
    }
}
