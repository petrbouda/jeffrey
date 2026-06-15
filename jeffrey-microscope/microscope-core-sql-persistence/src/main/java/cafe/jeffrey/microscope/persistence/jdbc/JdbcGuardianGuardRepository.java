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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.GuardianGroupSetting;
import cafe.jeffrey.microscope.persistence.api.GuardianGuard;
import cafe.jeffrey.microscope.persistence.api.GuardianGuardRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcGuardianGuardRepository implements GuardianGuardRepository {

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM guardians ORDER BY guard_id";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT * FROM guardians WHERE guard_id = :guard_id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO guardians
                (guard_id, name, enabled, built_in, group_kind, category, result_type, target_frame,
                 matching_type, info_threshold, warning_threshold, matcher_spec, preconditions,
                 summary_noun, explanation, solution, created_at)
            VALUES
                (:guard_id, :name, :enabled, :built_in, :group_kind, :category, :result_type, :target_frame,
                 :matching_type, :info_threshold, :warning_threshold, :matcher_spec, :preconditions,
                 :summary_noun, :explanation, :solution, :created_at)""";

    //language=SQL
    private static final String UPDATE = """
            UPDATE guardians SET
                name = :name, enabled = :enabled, built_in = :built_in, group_kind = :group_kind,
                category = :category, result_type = :result_type, target_frame = :target_frame,
                matching_type = :matching_type, info_threshold = :info_threshold,
                warning_threshold = :warning_threshold, matcher_spec = :matcher_spec,
                preconditions = :preconditions, summary_noun = :summary_noun, explanation = :explanation,
                solution = :solution
            WHERE guard_id = :guard_id""";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM guardians WHERE guard_id = :guard_id";

    //language=SQL
    private static final String SELECT_GROUP_SETTINGS =
            "SELECT * FROM guardian_group_settings ORDER BY group_kind";

    private final DatabaseClient databaseClient;

    public JdbcGuardianGuardRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.GUARDIAN_GUARDS);
    }

    @Override
    public List<GuardianGuard> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_GUARDIAN_GUARDS,
                SELECT_ALL,
                new MapSqlParameterSource(),
                guardMapper());
    }

    @Override
    public Optional<GuardianGuard> find(String guardId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("guard_id", guardId);
        return databaseClient.querySingle(
                StatementLabel.FIND_GUARDIAN_GUARD,
                SELECT_BY_ID,
                params,
                guardMapper());
    }

    @Override
    public void insert(GuardianGuard guard) {
        databaseClient.insert(StatementLabel.INSERT_GUARDIAN_GUARD, INSERT, guardParams(guard));
    }

    @Override
    public void update(GuardianGuard guard) {
        databaseClient.update(StatementLabel.UPDATE_GUARDIAN_GUARD, UPDATE, guardParams(guard));
    }

    @Override
    public void delete(String guardId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("guard_id", guardId);
        databaseClient.update(StatementLabel.DELETE_GUARDIAN_GUARD, DELETE, params);
    }

    @Override
    public List<GuardianGroupSetting> findAllGroupSettings() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_GUARDIAN_GROUP_SETTINGS,
                SELECT_GROUP_SETTINGS,
                new MapSqlParameterSource(),
                (rs, _) -> new GuardianGroupSetting(rs.getString("group_kind"), rs.getLong("min_samples")));
    }

    private static MapSqlParameterSource guardParams(GuardianGuard guard) {
        return new MapSqlParameterSource()
                .addValue("guard_id", guard.guardId())
                .addValue("name", guard.name())
                .addValue("enabled", guard.enabled())
                .addValue("built_in", guard.builtIn())
                .addValue("group_kind", guard.groupKind())
                .addValue("category", guard.category())
                .addValue("result_type", guard.resultType())
                .addValue("target_frame", guard.targetFrame())
                .addValue("matching_type", guard.matchingType())
                .addValue("info_threshold", guard.infoThreshold())
                .addValue("warning_threshold", guard.warningThreshold())
                .addValue("matcher_spec", guard.matcherSpec())
                .addValue("preconditions", guard.preconditions())
                .addValue("summary_noun", guard.summaryNoun())
                .addValue("explanation", guard.explanation())
                .addValue("solution", guard.solution())
                .addValue("created_at", guard.createdAt().atOffset(ZoneOffset.UTC));
    }

    private static RowMapper<GuardianGuard> guardMapper() {
        return (rs, _) -> {
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            return new GuardianGuard(
                    rs.getString("guard_id"),
                    rs.getString("name"),
                    rs.getBoolean("enabled"),
                    rs.getBoolean("built_in"),
                    rs.getString("group_kind"),
                    rs.getString("category"),
                    rs.getString("result_type"),
                    rs.getString("target_frame"),
                    rs.getString("matching_type"),
                    rs.getDouble("info_threshold"),
                    rs.getDouble("warning_threshold"),
                    rs.getString("matcher_spec"),
                    rs.getString("preconditions"),
                    rs.getString("summary_noun"),
                    rs.getString("explanation"),
                    rs.getString("solution"),
                    createdAt != null ? createdAt.toInstant() : null);
        };
    }
}
