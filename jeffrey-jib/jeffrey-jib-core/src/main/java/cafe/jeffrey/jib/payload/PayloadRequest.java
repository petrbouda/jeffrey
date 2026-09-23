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
