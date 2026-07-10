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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accumulates the details of a single run. Items are capped so a misbehaving job
 * (e.g. a cleaner touching thousands of files) cannot balloon the in-memory history;
 * overflowing items are counted and surfaced as one trailing "... and N more" line.
 * <p>
 * Methods are synchronized: the scheduler runs jobs single-threaded, but a job is
 * free to fan work out to its own threads and report from them.
 */
public final class CollectingJobExecutionReport implements JobExecutionReport {

    static final int MAX_ITEMS_PER_EXECUTION = 200;

    private static final String OVERFLOW_ITEM_FORMAT = "... and %d more";

    private final List<String> items = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private String summary;
    private int overflowedItems;

    @Override
    public synchronized void summary(String summary) {
        this.summary = summary;
    }

    @Override
    public synchronized void item(String item) {
        addItem(item);
    }

    @Override
    public synchronized void failure(String item) {
        failures.add(item);
        addItem(item);
    }

    private void addItem(String item) {
        if (items.size() < MAX_ITEMS_PER_EXECUTION) {
            items.add(item);
        } else {
            overflowedItems++;
        }
    }

    public synchronized String summary() {
        return summary;
    }

    public synchronized List<String> items() {
        if (overflowedItems == 0) {
            return List.copyOf(items);
        }
        List<String> withOverflow = new ArrayList<>(items);
        withOverflow.add(OVERFLOW_ITEM_FORMAT.formatted(overflowedItems));
        return List.copyOf(withOverflow);
    }

    public synchronized boolean hasFailures() {
        return !failures.isEmpty();
    }

    public synchronized Optional<String> firstFailure() {
        if (failures.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(failures.getFirst());
    }
}
