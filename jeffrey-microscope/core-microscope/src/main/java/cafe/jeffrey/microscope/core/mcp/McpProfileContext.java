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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.persistence.DatabaseLease;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * One profile, held open for an MCP client between tool calls.
 * <p>
 * The lease is the point of this type. A cached pool is idle-evicted after a few quiet minutes, and an
 * interactive session leaves far longer gaps than that while the reader thinks — so without a lease the
 * second question about a profile fails with "Failed to obtain JDBC Connection" even though the first
 * one worked.
 */
final class McpProfileContext implements AutoCloseable {

    private final ProfileManager profileManager;
    private final DatabaseLease lease;
    private Instant lastAccess;
    private int activeCalls;
    private boolean retired;
    private boolean closed;

    McpProfileContext(ProfileManager profileManager, DatabaseLease lease, Instant createdAt) {
        this.profileManager = profileManager;
        this.lease = lease;
        this.lastAccess = createdAt;
    }

    ProfileManager profileManager() {
        return profileManager;
    }

    DataSource dataSource() {
        return lease.dataSource();
    }

    synchronized void acquire(Instant now) {
        if (retired) {
            throw new IllegalStateException("Cannot acquire a retired MCP profile context");
        }
        activeCalls++;
        lastAccess = now;
    }

    synchronized void release(Instant now) {
        if (activeCalls == 0) {
            return;
        }
        activeCalls--;
        lastAccess = now;
        closeIfUnused();
    }

    synchronized boolean retireIfIdle(Instant threshold) {
        if (activeCalls > 0 || !lastAccess.isBefore(threshold)) {
            return false;
        }
        retire();
        return true;
    }

    synchronized void retire() {
        retired = true;
        closeIfUnused();
    }

    private void closeIfUnused() {
        if (retired && activeCalls == 0 && !closed) {
            closed = true;
            lease.close();
        }
    }

    @Override
    public void close() {
        retire();
    }
}
