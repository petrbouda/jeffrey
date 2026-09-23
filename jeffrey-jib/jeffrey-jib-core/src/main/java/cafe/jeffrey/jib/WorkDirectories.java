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

package cafe.jeffrey.jib;

import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the extension keeps the files it hands to JIB: the entrypoint script and the unpacked
 * payloads.
 *
 * <p>JIB keys its layer cache on each source file's path and modification time. Files that live
 * under the build's own output directory keep the same path from one build to the next and are
 * only rewritten when their content changes, so the payload layer — the largest thing this
 * extension adds — is archived and hashed once and then served from JIB's cache. The build's own
 * {@code clean} removes them along with everything else.
 */
public abstract class WorkDirectories {

    private static final String SUBDIRECTORY = "jeffrey-jib";
    private static final String TEMP_PREFIX = "jeffrey-jib-";

    /** The extension's corner of the build output directory ({@code target/} or {@code build/}). */
    public static Path under(Path buildDirectory) {
        return buildDirectory.resolve(SUBDIRECTORY);
    }

    /**
     * A fresh temporary directory, for when the build system did not say where its output goes.
     * Everything still works, but JIB sees new source paths on every build and re-archives the
     * payload layer each time, so the caller logs that it fell back.
     */
    public static Path temporary(Class<? extends JibPluginExtension> extensionClass, ExtensionLogger logger)
            throws JibPluginExtensionException {
        try {
            Path directory = Files.createTempDirectory(TEMP_PREFIX);
            directory.toFile().deleteOnExit();
            logger.log(LogLevel.WARN,
                    "jeffrey-jib: no build directory available; unpacking payloads into " + directory
                            + " (JIB will not be able to reuse the cached payload layer)");
            return directory;
        } catch (IOException e) {
            throw new JibPluginExtensionException(
                    extensionClass, "jeffrey-jib: could not create a temporary work directory", e);
        }
    }
}
