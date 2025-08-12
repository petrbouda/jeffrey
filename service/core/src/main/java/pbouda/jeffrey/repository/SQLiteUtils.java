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

package pbouda.jeffrey.repository;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;

public abstract class SQLiteUtils {
    /**
     * Default busy timeout for SQLite connections.
     * This is the time the connection will wait for a lock to be released before throwing an exception.
     */
    private static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(10);

    private static final String DATABASE_FILENAME = "workspace.db";

    public static DataSource workspace(Path basePath) {
        return notPooled(basePath.resolve(DATABASE_FILENAME));
    }

    public static DataSource notPooled(Path dbPath) {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
        config.setBusyTimeout((int) DEFAULT_BUSY_TIMEOUT.toMillis());

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(buildUrl(dbPath));
        return dataSource;
    }

    private static String buildUrl(Path dbPath) {
        return "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }
}
