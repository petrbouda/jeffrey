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
