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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.provider.profile.api.EventType;
import cafe.jeffrey.provider.profile.api.EnhancedEventType;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

import static cafe.jeffrey.provider.profile.jdbc.DuckDBAppenderUtils.nullableAppend;

public class DuckDBEventTypeWriter extends DuckDBBatchingWriter<EnhancedEventType> {

    public DuckDBEventTypeWriter(Executor executor, DataSource dataSource, int batchSize, BatchFlushLimit flushLimit) {
        super(executor, "event_types", dataSource, batchSize, StatementLabel.INSERT_EVENT_TYPES, flushLimit);
    }

    @Override
    public void execute(DuckDBConnection connection, List<EnhancedEventType> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender("event_types")) {
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
