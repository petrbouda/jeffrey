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

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * The two recovery actions offered by the recovery page. Both return the home directory
 * Jeffrey should continue booting with. Validation failures throw
 * {@link IllegalArgumentException} with user-readable messages that the page shows inline.
 */
public final class RecoveryActions {

    private static final Logger LOG = LoggerFactory.getLogger(RecoveryActions.class);

    private RecoveryActions() {
    }

    /**
     * Permanently removes the whole home directory content and recreates it empty. The regular
     * startup then recreates the subdirectories and a fresh database schema.
     */
    public static Path startFresh(Path homeDir) {
        LOG.warn("Recovery action, removing all data in the home directory: home_dir={}", homeDir);
        FileSystemUtils.removeAndCreateDirectories(homeDir);
        return homeDir;
    }

    /**
     * Points Jeffrey at a different home directory for THIS run, leaving the current one
     * untouched. The choice is deliberately not persisted: unless the user changes the home-dir
     * configuration permanently, the next start resolves the old directory again, fails
     * validation again and lands back on the recovery page.
     */
    public static Path switchHome(Path currentHome, String rawPath) {
        Path newHome = parseAbsolutePath(rawPath);

        if (newHome.equals(currentHome)) {
            throw new IllegalArgumentException("The new home folder must differ from the current one.");
        }
        if (newHome.startsWith(currentHome)) {
            throw new IllegalArgumentException("The new home folder must not be inside the current home folder.");
        }
        if (Files.exists(newHome) && !Files.isDirectory(newHome)) {
            throw new IllegalArgumentException("The path exists but is not a folder.");
        }

        FileSystemUtils.createDirectories(newHome);
        LOG.warn("Recovery action, switching to a different home directory: from={} to={}", currentHome, newHome);
        return newHome;
    }

    private static Path parseAbsolutePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Enter a path for the new home folder.");
        }

        Path path;
        try {
            path = Path.of(rawPath.trim());
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("The path is not valid.");
        }

        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("The path must be absolute.");
        }
        return path.normalize();
    }
}
