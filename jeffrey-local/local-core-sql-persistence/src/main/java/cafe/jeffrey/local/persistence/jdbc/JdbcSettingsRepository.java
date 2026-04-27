/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.local.persistence.jdbc;

import cafe.jeffrey.local.persistence.api.*;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.local.persistence.api.Setting;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;
import java.util.Optional;

public class JdbcSettingsRepository implements SettingsRepository {

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM settings ORDER BY category, name";

    //language=SQL
    private static final String SELECT_BY_CATEGORY =
            "SELECT * FROM settings WHERE category = :category ORDER BY name";

    //language=SQL
    private static final String SELECT_BY_CATEGORY_AND_NAME =
            "SELECT * FROM settings WHERE category = :category AND name = :name";

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO settings (category, name, value, secret)
            VALUES (:category, :name, :value, :secret)
            ON CONFLICT (category, name) DO UPDATE SET value = :value, secret = :secret""";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM settings WHERE category = :category AND name = :name";

    //language=SQL
    private static final String DELETE_BY_CATEGORY =
            "DELETE FROM settings WHERE category = :category";

    private final DatabaseClient databaseClient;

    public JdbcSettingsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.SETTINGS);
    }

    @Override
    public List<Setting> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_SETTINGS,
                SELECT_ALL,
                new MapSqlParameterSource(),
                settingMapper());
    }

    @Override
    public List<Setting> findByCategory(String category) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", category);

        return databaseClient.query(
                StatementLabel.FIND_SETTINGS_BY_CATEGORY,
                SELECT_BY_CATEGORY,
                params,
                settingMapper());
    }

    @Override
    public Optional<Setting> find(String category, String name) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", category)
                .addValue("name", name);

        return databaseClient.querySingle(
                StatementLabel.FIND_SETTING,
                SELECT_BY_CATEGORY_AND_NAME,
                params,
                settingMapper());
    }

    @Override
    public void upsert(Setting setting) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", setting.category())
                .addValue("name", setting.name())
                .addValue("value", setting.value())
                .addValue("secret", setting.secret());

        databaseClient.update(StatementLabel.UPSERT_SETTING, UPSERT, params);
    }

    @Override
    public void delete(String category, String name) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", category)
                .addValue("name", name);

        databaseClient.update(StatementLabel.DELETE_SETTING, DELETE, params);
    }

    @Override
    public void deleteByCategory(String category) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", category);

        databaseClient.update(StatementLabel.DELETE_SETTINGS_BY_CATEGORY, DELETE_BY_CATEGORY, params);
    }

    private static RowMapper<Setting> settingMapper() {
        return (rs, _) -> new Setting(
                rs.getString("category"),
                rs.getString("name"),
                rs.getString("value"),
                rs.getBoolean("secret"));
    }
}
