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

import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.sql.Condition;
import cafe.jeffrey.sql.SQLBuilder;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;

import static cafe.jeffrey.sql.SQLBuilder.*;

public abstract class SQLFormatter {

    /**
     * The value {@link ThreadInfo} carries when a thread id is not available.
     */
    private static final long UNKNOWN_THREAD_ID = -1;

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
            builder.and(gte("events.start_timestamp_from_beginning", l(from.toMillis())));
        }
        if (until != null) {
            builder.and(lt("events.start_timestamp_from_beginning", l(until.toMillis())));
        }
        return builder;
    }

    /**
     * Narrows the query to the given threads — one for a plain thread, all of them for a lane that
     * stands for a group.
     *
     * <p>The two id columns are matched separately. Java threads are identified by their Java id;
     * threads that never got one (a plain native thread reports {@code -1}) would all collide on
     * that value, so they are matched on the OS id instead. A group can hold both kinds, hence the
     * disjunction — and each side is emitted only when it has members, because an empty {@code IN ()}
     * is not valid SQL.
     */
    public SQLBuilder threadInfo(List<ThreadInfo> threads) {
        if (threads == null || threads.isEmpty()) {
            return new SQLBuilder();
        }

        List<Long> javaIds = threads.stream()
                .map(ThreadInfo::javaId)
                .filter(id -> id != UNKNOWN_THREAD_ID)
                .distinct()
                .toList();

        List<Long> osIds = threads.stream()
                .filter(thread -> thread.javaId() == UNKNOWN_THREAD_ID)
                .map(ThreadInfo::osId)
                .distinct()
                .toList();

        if (osIds.isEmpty()) {
            return new SQLBuilder().where(inLongs("threads.java_id", javaIds));
        }
        if (javaIds.isEmpty()) {
            return new SQLBuilder().where(inLongs("threads.os_id", osIds));
        }
        return new SQLBuilder().where(
                or(inLongs("threads.java_id", javaIds), inLongs("threads.os_id", osIds)));
    }
}
