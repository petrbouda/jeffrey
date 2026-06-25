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

package cafe.jeffrey.agent;

import jdk.jfr.FlightRecorder;

import java.lang.management.ManagementFactory;

/**
 * Emits the {@link AppInformationEvent} once at the start of every JFR chunk.
 * Registered through {@link FlightRecorder#addPeriodicEvent}, which — for an
 * event whose period is {@code "beginChunk"} — invokes the Runnable from JFR's
 * internal periodic thread at each chunk rotation.
 */
public class AppInformationEmitter implements Runnable {

    private final AppInformation identity;
    private final long jvmStartedAt;

    public AppInformationEmitter(AppInformation identity) {
        this.identity = identity;
        this.jvmStartedAt = ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    @Override
    public void run() {
        AppInformationEvent event = new AppInformationEvent();
        event.workspaceId = identity.workspaceId();
        event.projectId = identity.projectId();
        event.projectName = identity.projectName();
        event.projectLabel = identity.projectLabel();
        event.instanceId = identity.instanceId();
        event.sessionId = identity.sessionId();
        event.sessionOrder = identity.sessionOrder();
        event.attributes = identity.attributes();
        event.provisionedAt = identity.provisionedAt();
        event.jvmStartedAt = jvmStartedAt;
        event.commit();
    }

    public static void start(AppInformation identity) {
        FlightRecorder.addPeriodicEvent(AppInformationEvent.class, new AppInformationEmitter(identity));
    }
}
