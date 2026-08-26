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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventFrameWithHash;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips frames through the writer and back out via {@link FramesCache}, with the
 * hidden-class identity as the thing under test: it must survive storage, and it must be part of a
 * frame's hash so two lambdas of the same host class stay two rows.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBFrameWriterTest {

    private static final String LAMBDA_CLASS = "org.springframework.security.web.FilterChainProxy$$Lambda";
    private static final String LAMBDA_ADDRESS = "0x0000000011cb1be8";
    private static final String OTHER_LAMBDA_ADDRESS = "0x0000000028cb5fc8";
    private static final String INLINED = "Inlined";

    private static final SingleThreadHasher HASHER = new SingleThreadHasher();

    private static void write(DataSource dataSource, EventFrame... frames) {
        try (DuckDBFrameWriter writer = new DuckDBFrameWriter(
                Runnable::run, dataSource, frames.length, BatchFlushLimit.ofSlots(64))) {

            for (EventFrame frame : frames) {
                writer.insert(new EventFrameWithHash(HASHER.hashFrame(frame), frame));
            }
        }
    }

    private static FramesCache cacheOf(DataSource dataSource) {
        return FramesCache.load(new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_FRAMES));
    }

    private static EventFrame hiddenFrame(String address) {
        return new EventFrame(LAMBDA_CLASS, "doFilter", INLINED, 10, 0, address);
    }

    private static EventFrame ordinaryFrame() {
        return new EventFrame("com.example.OrderService", "process", INLINED, 4, 142);
    }

    @Nested
    class HiddenClassIdentity {

        @Test
        void survivesTheRoundTrip(DataSource dataSource) {
            EventFrame frame = hiddenFrame(LAMBDA_ADDRESS);
            write(dataSource, frame);

            JfrStackFrameImpl loaded = cacheOf(dataSource)
                    .resolveFrames(new long[]{HASHER.hashFrame(frame)})
                    .getFirst();

            assertEquals(LAMBDA_CLASS, loaded.method().clazz().className());
            assertEquals(LAMBDA_ADDRESS, loaded.method().clazz().hiddenClassId());
            assertTrue(loaded.method().clazz().isHidden());
        }

        @Test
        void isNullForAnOrdinaryClass(DataSource dataSource) {
            EventFrame frame = ordinaryFrame();
            write(dataSource, frame);

            JfrStackFrameImpl loaded = cacheOf(dataSource)
                    .resolveFrames(new long[]{HASHER.hashFrame(frame)})
                    .getFirst();

            assertNull(loaded.method().clazz().hiddenClassId());
            assertFalse(loaded.method().clazz().isHidden());
        }

        @Test
        void keepsTwoLambdasOfTheSameHostClassApart(DataSource dataSource) {
            EventFrame first = hiddenFrame(LAMBDA_ADDRESS);
            EventFrame second = hiddenFrame(OTHER_LAMBDA_ADDRESS);
            write(dataSource, first, second);

            assertNotEquals(HASHER.hashFrame(first), HASHER.hashFrame(second));

            List<JfrStackFrameImpl> loaded = cacheOf(dataSource)
                    .resolveFrames(new long[]{HASHER.hashFrame(first), HASHER.hashFrame(second)});

            assertEquals(2, loaded.size());
            assertEquals(LAMBDA_ADDRESS, loaded.get(0).method().clazz().hiddenClassId());
            assertEquals(OTHER_LAMBDA_ADDRESS, loaded.get(1).method().clazz().hiddenClassId());
        }
    }
}
