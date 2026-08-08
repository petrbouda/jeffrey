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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Persists the "use a different home folder" choice across restarts. The marker file
 * {@value #REDIRECT_FILE} inside a home directory contains the absolute path of the home
 * directory Jeffrey should use instead. Resolution follows chains of markers with a hop
 * limit and a cycle guard; anything invalid stops the chain at the last valid directory.
 */
public final class HomeRedirect {

    private static final Logger LOG = LoggerFactory.getLogger(HomeRedirect.class);

    public static final String REDIRECT_FILE = "jeffrey-home.redirect";

    private static final int MAX_HOPS = 5;

    private HomeRedirect() {
    }

    public static Path follow(Path homeDir) {
        Path current = homeDir.toAbsolutePath().normalize();
        Set<Path> visited = new HashSet<>();
        visited.add(current);

        for (int hop = 0; hop < MAX_HOPS; hop++) {
            Path target = readTarget(current);
            if (target == null) {
                return current;
            }
            if (!visited.add(target)) {
                LOG.warn("Home redirect chain contains a cycle, stopping: home_dir={} target={}", current, target);
                return current;
            }
            LOG.info("Following home redirect: from={} to={}", current, target);
            current = target;
        }

        LOG.warn("Home redirect chain exceeds maximum hops, stopping: home_dir={} max_hops={}", current, MAX_HOPS);
        return current;
    }

    public static void write(Path fromHome, Path toHome) {
        Path marker = fromHome.resolve(REDIRECT_FILE);
        try {
            Files.writeString(marker, toHome.toAbsolutePath().normalize().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write home redirect: marker=" + marker, e);
        }
    }

    private static Path readTarget(Path homeDir) {
        Path marker = homeDir.resolve(REDIRECT_FILE);
        if (!Files.isRegularFile(marker)) {
            return null;
        }

        String content;
        try {
            content = Files.readString(marker).trim();
        } catch (IOException e) {
            LOG.warn("Cannot read home redirect, ignoring it: marker={} message={}", marker, e.getMessage());
            return null;
        }

        if (content.isEmpty()) {
            LOG.warn("Home redirect is empty, ignoring it: marker={}", marker);
            return null;
        }

        Path target;
        try {
            target = Path.of(content);
        } catch (InvalidPathException e) {
            LOG.warn("Home redirect contains an invalid path, ignoring it: marker={} content={}", marker, content);
            return null;
        }

        if (!target.isAbsolute()) {
            LOG.warn("Home redirect must contain an absolute path, ignoring it: marker={} content={}", marker, content);
            return null;
        }
        return target.normalize();
    }
}
