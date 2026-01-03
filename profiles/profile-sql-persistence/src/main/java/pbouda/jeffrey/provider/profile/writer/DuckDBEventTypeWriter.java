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

package pbouda.jeffrey.provider.profile.writer;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;
import pbouda.jeffrey.provider.profile.model.EventType;
import pbouda.jeffrey.provider.profile.model.writer.EnhancedEventType;
import pbouda.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

import static pbouda.jeffrey.provider.profile.writer.DuckDBAppenderUtils.nullableAppend;

public class DuckDBEventTypeWriter extends DuckDBBatchingWriter<EnhancedEventType> {

    public DuckDBEventTypeWriter(Executor executor, DataSource dataSource, int batchSize) {
        super(executor, "event_types", dataSource, batchSize, StatementLabel.INSERT_EVENT_TYPES);
    }

    @Override
    public void execute(DuckDBConnection connection, List<EnhancedEventType> batch) throws Exception {
        DuckDBConnection unwrapped = DataSourceUtils.unwrapConnection(connection, DuckDBConnection.class);
        try (DuckDBAppender appender = unwrapped.createAppender("event_types")) {
            for (EnhancedEventType entity : batch) {
                EventType eventType = entity.eventType();

                appender.beginRow();
                // name - VARCHAR NOT NULL
                appender.append(eventType.name());
                // label - VARCHAR NOT NULL
                appender.append(eventType.label());
                // type_id - BIGINT (nullable)
                nullableAppend(appender, eventType.typeId());
                // description - VARCHAR (nullable)
                nullableAppend(appender, eventType.description());
                // categories - VARCHAR (nullable) - JSON array
                nullableAppend(appender, eventType.categories() != null ? Json.toString(eventType.categories()) : null);
                // source - VARCHAR NOT NULL
                appender.append(String.valueOf(entity.source().getId()));
                // subtype - VARCHAR (nullable)
                nullableAppend(appender, entity.subtype());
                // has_stacktrace - BOOLEAN NOT NULL
                appender.append(entity.containsStackTraces());
                // extras - VARCHAR (nullable) - JSON map
                nullableAppend(appender, entity.extras() != null ? Json.toString(entity.extras()) : null);
                // settings - VARCHAR (nullable) - JSON map
                nullableAppend(appender, entity.settings() != null ? Json.toString(entity.settings()) : null);
                // columns - VARCHAR (nullable) - JSON
                nullableAppend(appender, eventType.columns() != null ? eventType.columns().toString() : null);
                appender.endRow();
            }
        }
    }
}
