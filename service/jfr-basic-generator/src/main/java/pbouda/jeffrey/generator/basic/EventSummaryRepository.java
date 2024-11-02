/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.generator.basic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.generator.basic.event.EventSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class EventSummaryRepository {

    private static final String INSERT = """
            INSERT OR IGNORE INTO event_summary(
               name, label, samples, weight, has_stacktrace, categories,  params) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String GET_ALL = """
            SELECT * FROM event_summary
            """;

    private final JdbcTemplate jdbcTemplate;

    public EventSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(EventSummary eventSummary) {
        jdbcTemplate.update(INSERT,
                eventSummary.name(),
                eventSummary.label(),
                eventSummary.samples(),
                eventSummary.weight(),
                eventSummary.hasStacktrace(),
                Json.toPrettyString(eventSummary.categories()),
                Json.toPrettyString(eventSummary.extras()));
    }

    public void insertAll(Collection<EventSummary> settings) {
        settings.forEach(this::insert);
    }

    public List<EventSummary> all() {
        return jdbcTemplate.query(GET_ALL, mapper());
    }

    private static RowMapper<EventSummary> mapper() {
        return (rs, __) -> {
            String name = rs.getString("name");
            String label = rs.getString("label");
            long samples = rs.getLong("samples");
            long weight = rs.getLong("weight");
            boolean hasStacktrace = rs.getBoolean("has_stacktrace");
            List<String> categories = Json.toList(rs.getString("categories"));
            Map<String, String> extras = Json.toMap(rs.getString("extras"));
            return new EventSummary(name, label, samples, weight, hasStacktrace, categories, extras);
        };
    }
}
