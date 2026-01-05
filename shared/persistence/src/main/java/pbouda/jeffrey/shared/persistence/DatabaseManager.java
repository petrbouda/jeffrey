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

package pbouda.jeffrey.shared.persistence;

import javax.sql.DataSource;

public interface DatabaseManager {

    /**
     * Opens the database using a simple JDBC datasource.
     *
     * @param databaseUri the database URI (URL or file path)
     * @return a DataSource instance for the database
     */
    DataSource open(String databaseUri);

    /**
     * Runs Flyway migrations on the platform database.
     *
     * @param dataSource the DataSource to run migrations on
     */
    void runMigrations(DataSource dataSource);
}
