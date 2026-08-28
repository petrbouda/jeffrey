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

package cafe.jeffrey.shared.persistence.client;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Counts the records handed to a downstream {@link Consumer} — the streaming counterpart of
 * {@link CountingRowCallbackHandler}. A streamed query never materializes a list, so the row count
 * of a {@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent} can only come from the rows
 * actually flowing through the terminal operation.
 * <p>
 * The count is incremented before the record is delegated, so a consumer that throws mid-stream
 * still leaves the record it failed on counted — the event commits with the rows the statement got
 * to, not with zero.
 */
public class CountingConsumer<T> implements Consumer<T> {

    private final LongAdder counter = new LongAdder();

    private final Consumer<T> consumer;

    public CountingConsumer(Consumer<T> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void accept(T record) {
        counter.increment();
        consumer.accept(record);
    }

    public long getRowCount() {
        return counter.longValue();
    }
}
