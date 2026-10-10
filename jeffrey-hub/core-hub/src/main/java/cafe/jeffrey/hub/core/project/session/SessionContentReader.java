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

package cafe.jeffrey.hub.core.project.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Tells whether a session directory holds anything the session recorded.
 *
 * <p>Hidden entries do not count: the provisioner creates {@code .heartbeat/} and the session
 * marker, and writes the agent jar, before the JVM starts, so they are there whether or not
 * anything ever ran. This is the same filter the repository listing applies, which is why a
 * session this reader calls empty is one the UI shows with "0 sources".</p>
 *
 * <p>Lists without stat-ing first, for the reason {@link FileHeartbeatReader} reads that way: only
 * a missing directory reports {@code NoSuchFileException}, while one this hub may not traverse
 * arrives as an ordinary {@code IOException}, and the two must not be flattened into "empty".</p>
 */
public class SessionContentReader {

    private static final Logger LOG = LoggerFactory.getLogger(SessionContentReader.class);

    private static final String REASON_MISSING = "the session directory does not exist";
    private static final String REASON_NOT_A_DIRECTORY = "the session path is not a directory";
    private static final String REASON_UNLISTABLE = "the session directory could not be listed";

    /**
     * @param sessionPath path to the session directory
     */
    public SessionContentRead read(Path sessionPath) {
        try (Stream<Path> entries = Files.list(sessionPath)) {
            boolean holdsData = entries.anyMatch(FileSystemUtils::isNotHidden);
            return holdsData ? SessionContentRead.holdsData() : SessionContentRead.empty();
        } catch (NoSuchFileException e) {
            return SessionContentRead.unreadable(REASON_MISSING);
        } catch (NotDirectoryException e) {
            return SessionContentRead.unreadable(REASON_NOT_A_DIRECTORY);
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Session directory cannot be listed, session left alone: session_path={}", sessionPath, e);
            return SessionContentRead.unreadable(REASON_UNLISTABLE);
        }
    }
}
