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

package pbouda.jeffrey.provider.profile.query;

import pbouda.jeffrey.shared.common.model.ThreadInfo;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.sql.Condition;
import pbouda.jeffrey.sql.SQLBuilder;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;

import static pbouda.jeffrey.sql.SQLBuilder.*;

public abstract class SQLFormatter {

    private final BiFunction<String, String, String> jsonbColumnFormatter;

    public SQLFormatter(BiFunction<String, String, String> jsonbColumnFormatter) {
        this.jsonbColumnFormatter = jsonbColumnFormatter;
    }

    /**
     * Formats the given SQL to be compatible with JSON operations of the specific database.
     * The generic format: {column_name}::jsonb {taken from Postgres},
     * and it formats it to the database-specific format.
     *
     * @param sql the SQL string to format
     * @return the formatted SQL string specifically for the given database
     */
    public abstract String formatJson(String sql);

    public SQLBuilder eventFields() {
        return new SQLBuilder()
                .addColumn(jsonbColumnFormatter.apply("events.fields", "event_fields"));
    }

    public Condition eventType(Type eventType) {
        return eq("events.event_type", l(eventType.code()));
    }

    public Condition eventTypes(List<Type> eventTypes) {
        if (eventTypes.size() == 1) {
            return eventType(eventTypes.getFirst());
        }

        List<String> typeCodes = eventTypes.stream()
                .map(Type::code)
                .toList();

        return in("events.event_type", typeCodes);
    }

    public SQLBuilder threads() {
        return new SQLBuilder()
                .addColumn("threads.java_id")
                .addColumn("threads.os_id")
                .addColumn("threads.is_virtual")
                .addColumn("threads.name")
                .join("threads", eq("events.thread_hash", c("threads.thread_hash")));
    }

    public SQLBuilder eventTypesInfo() {
        return new SQLBuilder()
                .addColumn("event_types.label")
                .join("event_types", eq("events.event_type", c("event_types.name")));
    }

    public SQLBuilder timeRangeOptional(Duration from, Duration until) {
        SQLBuilder builder = new SQLBuilder();
        if (from != null) {
            builder.and(gte("EPOCH_MS(events.start_timestamp - fs.first_ts)", l(from.toMillis())));
        }
        if (until != null) {
            builder.and(lt("EPOCH_MS(events.start_timestamp - fs.first_ts)", l(until.toMillis())));
        }
        return builder;
    }

    public SQLBuilder threadInfo(ThreadInfo threadInfo) {
        if (threadInfo == null) {
            return new SQLBuilder();
        }
        return new SQLBuilder().where("threads.java_id", "=", l(threadInfo.javaId()));
    }
}
