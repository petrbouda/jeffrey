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

package cafe.jeffrey.intellij.dto;

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
