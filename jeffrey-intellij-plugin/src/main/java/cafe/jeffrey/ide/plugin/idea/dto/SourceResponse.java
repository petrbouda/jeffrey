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
 * Result of {@code GET /api/jeffrey/source}: the raw source text of a class, for display inside
 * Microscope's source viewer. {@code decompiled} is true when the text comes from a decompiled
 * library class (no sources attached).
 */
public record SourceResponse(
        boolean resolved,
        @Nullable String content,
        @Nullable String file,
        boolean decompiled,
        @Nullable String reason
) {

    public static SourceResponse notResolved(String reason) {
        return new SourceResponse(false, null, null, false, reason);
    }
}
