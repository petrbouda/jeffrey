/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.agent;

import cafe.jeffrey.jfr.events.JeffreyEventRegistry;
import cafe.jeffrey.jfr.events.heartbeat.HeartbeatEvent;
import cafe.jeffrey.jfr.events.message.AlertEvent;
import cafe.jeffrey.jfr.events.message.MessageEvent;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import java.lang.instrument.Instrumentation;
import java.time.Duration;

public class JeffreyAgent {

    private static final String RECORDING_NAME = "jeffrey-streaming";
    private static final Duration RECORDING_MAX_AGE = Duration.ofDays(2);

    public static void premain(String args, Instrumentation inst) {
        // Create a new recording with custom settings
        Recording recording = new Recording();
        Runtime.getRuntime().addShutdownHook(
                Thread.ofPlatform()
                        .name("jeffrey-agent-shutdown")
                        .unstarted(recording::close)
        );

        recording.setName(RECORDING_NAME);

        // Disable ALL registered events (Jeffrey custom events)
        for (var et : JeffreyEventRegistry.all()) {
            recording.disable(et);
        }

        // Disable ALL registered events (JDK built-in)
        for (EventType eventType : FlightRecorder.getFlightRecorder().getEventTypes()) {
            recording.disable(eventType.getName());
        }

        recording.enable(AlertEvent.class);
        recording.enable(MessageEvent.class);
        recording.enable(HeartbeatEvent.class);
        recording.setMaxAge(RECORDING_MAX_AGE);
        recording.start();

        // Add a periodic event that emits a HeartbeatEvent every second
        FlightRecorder.addPeriodicEvent(HeartbeatEvent.class, HeartbeatEmitter.INSTANCE);
    }
}
