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

package cafe.jeffrey.hub.core.scheduler.history;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectingJobExecutionReportTest {

    private final CollectingJobExecutionReport report = new CollectingJobExecutionReport();

    @Nested
    class Summary {

        @Test
        void lastWriteWins() {
            report.summary("first");
            report.summary("second");

            assertEquals("second", report.summary());
        }

        @Test
        void nullWhenNeverSet() {
            assertNull(report.summary());
        }
    }

    @Nested
    class Items {

        @Test
        void keepsItemsInInsertionOrder() {
            report.item("one");
            report.item("two");

            assertEquals(List.of("one", "two"), report.items());
        }

        @Test
        void overflowingItems_areCollapsedIntoOneTrailingLine() {
            int overflow = 7;
            for (int i = 0; i < CollectingJobExecutionReport.MAX_ITEMS_PER_EXECUTION + overflow; i++) {
                report.item("item-" + i);
            }

            List<String> items = report.items();
            assertEquals(CollectingJobExecutionReport.MAX_ITEMS_PER_EXECUTION + 1, items.size());
            assertEquals("... and " + overflow + " more", items.getLast());
        }
    }

    @Nested
    class Failures {

        @Test
        void failureAppearsAsItem_andFlagsTheRun() {
            report.item("processed: event-1");
            report.failure("failed: event-2");

            assertTrue(report.hasFailures());
            assertEquals("failed: event-2", report.firstFailure().orElseThrow());
            assertEquals(List.of("processed: event-1", "failed: event-2"), report.items());
        }

        @Test
        void noFailuresByDefault() {
            report.item("processed: event-1");

            assertFalse(report.hasFailures());
            assertTrue(report.firstFailure().isEmpty());
        }
    }
}
