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

package cafe.jeffrey.microscope.core.recovery;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Boot-relevant configuration resolved before Spring starts: the Jeffrey home directory, an
 * optional external core-database URL (which disables file-based recovery) and the HTTP port
 * the recovery page binds to.
 */
public record ResolvedBootConfig(Path homeDir, Optional<String> externalDatabaseUrl, int port) {
}
