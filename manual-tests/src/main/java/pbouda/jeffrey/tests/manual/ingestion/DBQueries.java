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

package pbouda.jeffrey.tests.manual.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class DBQueries {

    private record ValueByType(long val, String type) {
    }

    private static final RowMapper<ValueByType> VALUE_BY_TYPE_ROW_MAPPER =
            (rs, __) -> new ValueByType(rs.getLong("val"), rs.getString("event_type"));

    private static final String SAMPLES_BY_TYPE =
            "SELECT sum(samples) as val, event_type FROM events GROUP BY event_type";

    private static final String SAMPLES_TOTAL =
            "SELECT samples FROM event_types WHERE name = ?";

    public static Map<String, Long> samplesByType(JdbcTemplate jdbcTemplate) {
        List<ValueByType> list = jdbcTemplate.query(SAMPLES_BY_TYPE, VALUE_BY_TYPE_ROW_MAPPER);
        return list.stream().collect(Collectors.toMap(ValueByType::type, ValueByType::val));
    }

    public static long samplesTotal(JdbcTemplate jdbcTemplate, String eventType) {
        return jdbcTemplate.queryForObject(SAMPLES_TOTAL, Long.class, eventType);
    }
}
