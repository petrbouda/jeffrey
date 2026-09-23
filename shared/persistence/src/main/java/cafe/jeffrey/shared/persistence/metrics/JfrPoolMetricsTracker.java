/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.persistence.metrics;

import cafe.jeffrey.jfr.events.jdbc.pool.*;
import com.zaxxer.hikari.metrics.IMetricsTracker;

public class JfrPoolMetricsTracker implements IMetricsTracker {

    private final String poolName;

    public JfrPoolMetricsTracker(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public void recordConnectionCreatedMillis(long createdMs) {
        PooledJdbcConnectionCreatedEvent event = new PooledJdbcConnectionCreatedEvent();
        event.poolName = this.poolName;
        event.elapsedTime = createdMs * 1_000_000;
        event.commit();
    }

    @Override
    public void recordConnectionAcquiredNanos(long acquiredNs) {
        PooledJdbcConnectionAcquiredEvent event = new PooledJdbcConnectionAcquiredEvent();
        event.poolName = this.poolName;
        event.elapsedTime = acquiredNs;
        event.commit();
    }

    @Override
    public void recordConnectionUsageMillis(long borrowedMs) {
        PooledJdbcConnectionBorrowedEvent event = new PooledJdbcConnectionBorrowedEvent();
        event.poolName = this.poolName;
        event.elapsedTime = borrowedMs * 1_000_000;
        event.commit();
    }

    @Override
    public void recordConnectionTimeout() {
        AcquiringPooledJdbcConnectionTimeoutEvent event = new AcquiringPooledJdbcConnectionTimeoutEvent();
        event.poolName = this.poolName;
        event.commit();
    }
}
