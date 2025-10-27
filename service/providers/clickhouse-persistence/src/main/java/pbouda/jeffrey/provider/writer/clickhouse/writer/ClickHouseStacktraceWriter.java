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

package pbouda.jeffrey.provider.writer.clickhouse.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.api.model.writer.EventStacktraceWithHash;
import pbouda.jeffrey.provider.writer.clickhouse.ClickHouseClient;
import pbouda.jeffrey.provider.writer.clickhouse.model.ClickHouseStacktrace;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

public class ClickHouseStacktraceWriter extends
        ClickHouseBatchingWriter<EventStacktraceWithHash, ClickHouseStacktrace> {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseStacktraceWriter.class);

    public ClickHouseStacktraceWriter(ClickHouseClient clickHouseClient, String profileId, int batchSize) {
        super("stacktraces", clickHouseClient, batchSize, StatementLabel.INSERT_STACKTRACES);
    }

    @Override
    protected ClickHouseStacktrace entityMapper(EventStacktraceWithHash entity) {
        return null;
    }
}
