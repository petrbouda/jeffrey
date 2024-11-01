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

package pbouda.jeffrey.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSettingsRepository {

    private record TempSetting(String event, String name, String value) {
    }

    private static final String INSERT = """
            INSERT INTO active_settings (event, name, value) VALUES (?, ?, ?)
                ON CONFLICT (event, name) DO UPDATE SET value = EXCLUDED.value
                         WHERE active_settings.event = EXCLUDED.event
                         AND active_settings.name = EXCLUDED.name
            """;

    private static final String GET_ALL = """
            SELECT * FROM active_settings
            """;

    private final JdbcTemplate jdbcTemplate;

    public ActiveSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(ActiveSetting setting) {
        for (var entry : setting.params().entrySet()) {
            try {
                jdbcTemplate.update(INSERT, setting.event(), entry.getKey(), entry.getValue());
            } catch (Exception e) {
                throw new RuntimeException("Failed to insert setting: " + setting, e);
            }
        }
    }

    public void insertAll(Collection<ActiveSetting> settings) {
        settings.forEach(this::insert);
    }

    public Map<String, ActiveSetting> all() {
        List<TempSetting> ts = jdbcTemplate.query(GET_ALL, mapper());
        Map<String, ActiveSetting> combined = new HashMap<>();
        ts.forEach(ts1 -> {
            ActiveSetting setting = combined.computeIfAbsent(ts1.event(), ActiveSetting::new);
            setting.putParam(ts1.name(), ts1.value());
        });
        return combined;
    }

    private static RowMapper<TempSetting> mapper() {
        return (rs, __) -> {
            String event = rs.getString("event");
            String name = rs.getString("name");
            String value = rs.getString("value");
            return new TempSetting(event, name, value);
        };
    }
}
