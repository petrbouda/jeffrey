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


package cafe.jeffrey.hub.core.config;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Writes a scope's rendered configuration where the provisioner reads it.
 *
 * <p>The hub is the only writer of these files and never reads back anything but its own. A file is
 * a projection of what the database holds, so publishing is idempotent: the same values produce the
 * same bytes, and an unchanged file is left alone.</p>
 */
public interface ScopedConfigPublisher {

    /**
     * Writes the content for a scope, or removes the file when the content is empty.
     *
     * @return the digest of what the scope's file now holds, empty when it has none
     */
    String publish(Path scopeDir, String content);

    /** The content of a scope's file as it is on disk, for adopting a file with no stored values. */
    Optional<String> read(Path scopeDir);
}
