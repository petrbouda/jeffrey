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

package cafe.jeffrey.ide.plugin.idea.dto;

import org.jetbrains.annotations.Nullable;

/**
 * One open project window in an IDE instance. {@code id} is {@code Project.getLocationHash()} — a
 * stable identifier Microscope caches per profile to keep targeting the same window.
 *
 * <p>{@code vcsBranch} and {@code headCommit} describe the checkout the window is sitting on. They
 * are what lets Microscope compare a window against the commit a recording was tagged with, so a
 * reader mapping frames to code is told when the two have diverged instead of quietly reading a
 * different version of the file. Both are null when there is no readable repository.
 */
public record ProjectInfo(
        String id,
        String name,
        @Nullable String basePath,
        boolean trusted,
        boolean focused,
        @Nullable String vcsBranch,
        @Nullable String headCommit
) {
}
