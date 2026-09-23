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
 * Body of {@code POST /api/jeffrey/navigate}. {@code projectId} selects the target window
 * ({@code Project.getLocationHash()}); the rest identifies the source location.
 *
 * <p>v1 navigates by class + line (method-level/bytecode-precise matching is deferred until
 * Microscope sends a JVM descriptor). {@code lineNumber} is 1-based, or {@code <= 0} when unknown.
 */
public record NavigateRequest(
        String projectId,
        String className,
        @Nullable String methodName,
        int lineNumber,
        @Nullable String recordingTime
) {
}
