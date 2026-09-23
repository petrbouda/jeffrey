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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Per-loader payload served by the Loader Detail drawer. Combines the
 * loader's identifying header (display name, type classification, parent
 * pointer), its unloadability diagnostic, and the full set of classes it
 * defined. {@code parentLoaderId} is {@code 0} when the parent is the
 * synthetic bootstrap loader.
 */
public record ClassLoaderDetail(
        long loaderId,
        String displayName,
        long parentLoaderId,
        String parentDisplayName,
        LoaderType type,
        ClassLoaderUnloadability unloadability,
        long retainedSize,
        int classCount,
        long instanceCount,
        List<ClassEntry> classes) {
}
