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

import cafe.jeffrey.shared.common.IDGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Lays down a run's instance and session directories under its project, which is all the volume
 * says of them: an instance is its directory, named after the instance, and a session is a
 * directory named with a time-ordered id that carries its start. Nothing is written into them; the
 * profiler's recordings carry the machine's UTC offset in their names instead ({@link SessionLayout#recordingFilePattern}).
 *
 * <p>The project and the instance are reused by every run of the same application; the session is
 * always new.
 */
public class SessionRegistrar {

    private final LayoutProvisioner layoutProvisioner;
    private final Clock clock;

    /**
     * @param clock when the session starts, carried in its name, and the zone the profiled machine
     *              runs in, whose offset at that start async-profiler's {@code %t} is local to
     */
    public SessionRegistrar(LayoutProvisioner layoutProvisioner, Clock clock) {
        this.layoutProvisioner = layoutProvisioner;
        this.clock = clock;
    }

    /** Ensures the instance's directory exists, then creates this run's session directory. */
    public ProvisionedSession openSession(InitConfig config, ProjectLayout layout) throws IOException {
        String instanceId = config.getInstanceName();
        Path instancePath = LayoutProvisioner.createDirectories(layout.project().resolve(instanceId));

        String sessionId = IDGenerator.generate(clock);
        ZoneOffset offset = clock.getZone().getRules().getOffset(clock.instant());
        SessionLayout sessionLayout =
                layoutProvisioner.provisionSession(layout, instancePath.resolve(sessionId), offset);

        return new ProvisionedSession(instanceId, sessionId, sessionLayout);
    }
}
