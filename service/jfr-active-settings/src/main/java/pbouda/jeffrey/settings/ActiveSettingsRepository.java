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
import pbouda.jeffrey.common.Type;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSettingsRepository {

    private record TempSetting(String event, String label, String name, String value) {
    }

    private static final String INSERT = """
            INSERT OR IGNORE INTO active_settings (event, label, name, value) VALUES (?, ?, ?, ?)
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
                jdbcTemplate.update(INSERT, setting.event().code(), setting.label(), entry.getKey(), entry.getValue());
            } catch (Exception e) {
                throw new RuntimeException("Failed to insert setting: " + setting, e);
            }
        }
    }

    public void insertAll(Collection<ActiveSetting> settings) {
        settings.forEach(this::insert);
    }

    public Map<SettingNameLabel, ActiveSetting> all() {
        List<TempSetting> ts = jdbcTemplate.query(GET_ALL, mapper());
        Map<SettingNameLabel, ActiveSetting> combined = new HashMap<>();
        ts.forEach(ts1 -> {
            SettingNameLabel key = new SettingNameLabel(ts1.event, ts1.label);
            ActiveSetting setting = combined.computeIfAbsent(
                    key, k -> new ActiveSetting(Type.fromCode(k.name()), k.label()));
            setting.putParam(ts1.name(), ts1.value());
        });
        return combined;
    }

    private static RowMapper<TempSetting> mapper() {
        return (rs, __) -> {
            String event = rs.getString("event");
            String label = rs.getString("label");
            String name = rs.getString("name");
            String value = rs.getString("value");
            return new TempSetting(event, label, name, value);
        };
    }
}
