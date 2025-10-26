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

package pbouda.jeffrey.provider.writer.sql.client;

import pbouda.jeffrey.provider.api.DataSourceUtils;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;

import javax.sql.DataSource;

public class DatabaseClientProvider implements AutoCloseable {

    private final DataSource dataSource;
    private final boolean walCheckpointEnabled;

    public DatabaseClientProvider(DataSource dataSource, boolean walCheckpointEnabled) {
        this.dataSource = dataSource;
        this.walCheckpointEnabled = walCheckpointEnabled;
    }

    public DatabaseClient provide(GroupLabel groupLabel) {
        return new DatabaseClient(dataSource, groupLabel, walCheckpointEnabled);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        DataSourceUtils.close(dataSource);
    }
}
