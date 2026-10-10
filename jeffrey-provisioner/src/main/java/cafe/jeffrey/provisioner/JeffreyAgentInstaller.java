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
package cafe.jeffrey.provisioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Writes the Jeffrey agent the provisioner carries into the session directory, where the JVM loads
 * it from with {@code -javaagent}.
 *
 * <p>The agent jar is a resource of the provisioner rather than a file shipped beside it, so the jar
 * and native builds, and every image jeffrey-jib bakes them into, attach an agent of the same
 * release with nothing more to distribute or point at. One copy per session, because the session
 * directory is the one place a run is sure it can write, it is the one the JVM already reads its
 * other Jeffrey files from, and the agent is a few kilobytes. The file is hidden, so the hub never
 * lists it among the session's recordings.
 *
 * <p>Fails open like the profiler does: a provisioner built without the agent, or a session
 * directory that refuses the file, provisions a session without it rather than failing the start.
 */
public class JeffreyAgentInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyAgentInstaller.class);

    /** Where the build puts the agent jar inside the provisioner; must match the provisioner's pom. */
    static final String BUNDLED_RESOURCE = "cafe/jeffrey/provisioner/agent/jeffrey-agent.jar";

    /** The agent's file name inside the session directory. Hidden, so the hub does not list it. */
    public static final String SESSION_FILE = ".jeffrey-agent.jar";

    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final ClassLoader resources;
    private final String resource;

    public JeffreyAgentInstaller() {
        this(JeffreyAgentInstaller.class.getClassLoader(), BUNDLED_RESOURCE);
    }

    JeffreyAgentInstaller(ClassLoader resources, String resource) {
        this.resources = resources;
        this.resource = resource;
    }

    /**
     * @return the agent jar written into {@code sessionPath}, or empty when there is none to attach
     */
    public Optional<Path> install(Path sessionPath) {
        Path target = sessionPath.resolve(SESSION_FILE);
        Path temporary = sessionPath.resolve(SESSION_FILE + TEMPORARY_SUFFIX);
        try (InputStream agent = resources.getResourceAsStream(resource)) {
            if (agent == null) {
                LOG.warn("Jeffrey agent is not bundled with this provisioner, the session runs without it: resource={}",
                        resource);
                return Optional.empty();
            }
            Files.copy(agent, temporary, REPLACE_EXISTING);
            move(temporary, target);
            LOG.debug("Jeffrey agent written into the session: agentPath={}", target);
            return Optional.of(target);
        } catch (IOException e) {
            LOG.warn("Jeffrey agent cannot be written into the session, the session runs without it: agentPath={}",
                    target, e);
            deleteQuietly(temporary);
            return Optional.empty();
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // best-effort cleanup of a scratch file
        }
    }
}
