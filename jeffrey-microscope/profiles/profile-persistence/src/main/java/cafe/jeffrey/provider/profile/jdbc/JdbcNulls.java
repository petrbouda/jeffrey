/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reading a nullable column without letting JDBC's primitive getters flatten the null away.
 * <p>
 * Every column this is used for has a null that means something the derivation deliberately
 * normalised to: a root span has no parent, a notification that carried no span has no span. Reading
 * those with {@code getLong} would turn them into {@code 0}, which is the very value the derivation
 * took out so that {@code IS NULL} could carry the meaning instead.
 */
final class JdbcNulls {

    private JdbcNulls() {
    }

    /** The column's value, or {@code null} when SQL said NULL rather than zero. */
    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
