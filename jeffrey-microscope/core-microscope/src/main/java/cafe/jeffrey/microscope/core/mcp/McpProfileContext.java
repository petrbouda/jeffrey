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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.persistence.DatabaseLease;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<Instant> lastAccess;

    McpProfileContext(ProfileManager profileManager, DatabaseLease lease, Instant createdAt) {
        this.profileManager = profileManager;
        this.lease = lease;
        this.lastAccess = new AtomicReference<>(createdAt);
    }

    ProfileManager profileManager() {
        return profileManager;
    }

    DataSource dataSource() {
        return lease.dataSource();
    }

    void touch(Instant now) {
        lastAccess.set(now);
    }

    boolean idleSince(Instant threshold) {
        return lastAccess.get().isBefore(threshold);
    }

    @Override
    public void close() {
        lease.close();
    }
}
