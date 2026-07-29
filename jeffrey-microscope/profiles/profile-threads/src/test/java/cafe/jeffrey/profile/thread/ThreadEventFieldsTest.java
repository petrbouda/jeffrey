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

package cafe.jeffrey.profile.thread;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The tooltip renders values positionally against the labels the metadata declares, so a state that
 * produces a different number of values than it declares fields silently shifts every row onto the
 * wrong label. Nothing about the wire format catches that — this does.
 */
class ThreadEventFieldsTest {

    private static final Instant RECORDING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration START = Duration.ofMillis(100);
    private static final RelativeTimeRange WINDOW =
            new RelativeTimeRange(START, START.plusMillis(1));

    @ParameterizedTest
    @EnumSource(ThreadState.class)
    void produceExactlyAsManyValuesAsTheStateDeclaresFields(ThreadState state) {
        assumeTrue(state.hasEventDetail(), "The lifespan states have no event to look up");

        ThreadWindowEventsBuilder builder = new ThreadWindowEventsBuilder(WINDOW, 1);
        builder.onRecord(event(state));
        ThreadWindowEvents events = builder.build();

        assertEquals(
                metadataOf(state).fields().size(),
                events.events().getFirst().values().size(),
                "Values are positional, so " + state + " must emit one per declared field");
    }

    private static EventMetadata metadataOf(ThreadState state) {
        ThreadMetadata metadata = DbBasedThreadProvider.metadata();
        return switch (state) {
            case STARTED, ENDED -> metadata.lifespan();
            case PARKED -> metadata.parked();
            case BLOCKED -> metadata.blocked();
            case WAITING -> metadata.waiting();
            case SLEEP -> metadata.sleep();
            case SOCKET_READ -> metadata.socketRead();
            case SOCKET_WRITE -> metadata.socketWrite();
            case FILE_READ -> metadata.fileRead();
            case FILE_WRITE -> metadata.fileWrite();
        };
    }

    /**
     * An event carrying none of its optional JSON fields — the values still have to be emitted, as
     * gaps, or the ones that follow land on the wrong label.
     */
    private static GenericRecord event(ThreadState state) {
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        return new GenericRecord(
                state.eventType(),
                null,
                RECORDING_START.plus(START),
                START,
                Duration.ofNanos(1_000),
                new JfrThreadImpl(1010, 10, "worker-1", false),
                null,
                1,
                0,
                fields);
    }

    /**
     * Guards the assumption the parameterised test leans on: every state that claims event detail is
     * one the builder can actually turn into values.
     */
    @ParameterizedTest
    @EnumSource(ThreadState.class)
    void areDeclaredForEveryStateThatClaimsEventDetail(ThreadState state) {
        assumeTrue(state.hasEventDetail());

        List<ThreadField> declared = metadataOf(state).fields();

        assertEquals(
                declared.size(),
                declared.stream().distinct().count(),
                state + " declares the same field twice");
    }
}
