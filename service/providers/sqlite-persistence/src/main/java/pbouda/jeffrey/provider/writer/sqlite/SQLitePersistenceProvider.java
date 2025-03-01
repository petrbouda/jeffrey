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

package pbouda.jeffrey.provider.writer.sqlite;

import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.repository.Repositories;

import javax.sql.DataSource;
import java.util.Map;

public class SQLitePersistenceProvider implements PersistenceProvider {

    private static final String DEFAULT_BUSY_TIMEOUT = "3000";
    private static final String DEFAULT_BATCH_SIZE = "3000";

    private DataSource datasource;
    private int batchSize;

    @Override
    public void initialize(Map<String, String> properties) {
        Integer busyTimeout = Integer.parseInt(properties.getOrDefault("writer.busy-timeout-ms", DEFAULT_BUSY_TIMEOUT));
        this.batchSize = Integer.parseInt(properties.getOrDefault("writer.batch-size", DEFAULT_BATCH_SIZE));
        String url = properties.get("writer.url");
        this.datasource = DataSourceUtils.notPooled(url, busyTimeout);
    }

    @Override
    public DataSource dataSource() {
        return datasource;
    }

    @Override
    public EventWriter newWriter() {
        return new SQLiteEventWriter(datasource, batchSize);
    }

    @Override
    public Repositories repositories() {
        return new JdbcRepositories(datasource);
    }

    @Override
    public void close() {
    }
}
