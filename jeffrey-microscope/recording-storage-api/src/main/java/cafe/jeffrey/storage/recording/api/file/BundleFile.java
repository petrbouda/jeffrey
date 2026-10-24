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

package cafe.jeffrey.storage.recording.api.file;

import java.time.Instant;

/**
 * A file of a {@link Bundle} that never becomes a profile — a GC or JVM log, an application log, a
 * crash file. Listed with the bundle and downloadable; an agent reads it as a path, never parsed here.
 */
public record BundleFile(
        String id,
        String bundleId,
        String filename,
        ManagedFile fileType,
        Instant uploadedAt,
        long sizeInBytes) {
}
