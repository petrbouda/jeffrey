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
 * One open project window in an IDE instance. {@code id} is {@code Project.getLocationHash()} — a
 * stable identifier Microscope caches per profile to keep targeting the same window.
 */
public record ProjectInfo(
        String id,
        String name,
        @Nullable String basePath,
        boolean trusted,
        boolean focused,
        @Nullable String vcsBranch
) {
}
