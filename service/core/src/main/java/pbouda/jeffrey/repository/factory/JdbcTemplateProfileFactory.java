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

package pbouda.jeffrey.repository.factory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.common.filesystem.ProfileDirs;

import javax.sql.DataSource;
import java.nio.file.Path;

public abstract class JdbcTemplateProfileFactory {

    private static final SQLiteConfig CONFIG;

    static {
        CONFIG = new SQLiteConfig();
        CONFIG.setJournalMode(JournalMode.WAL);
        CONFIG.setSynchronous(SynchronousMode.OFF);
        CONFIG.setTransactionMode(TransactionMode.DEFERRED);
    }

    public static JdbcTemplate createCommon(ProfileDirs profileDirs) {
        return createForDbFile(profileDirs.databaseCommon());
    }

    public static JdbcTemplate createEvents(ProfileDirs profileDirs) {
        return createForDbFile(profileDirs.databaseEvents());
    }

    public static DataSource createDataSourceForEvents(ProfileDirs profileDirs) {
        SQLiteDataSource dataSource = new SQLiteDataSource(CONFIG);
        dataSource.setUrl("jdbc:sqlite:" + profileDirs.databaseEvents());
        return dataSource;
    }

    private static JdbcTemplate createForDbFile(Path dbFile) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbFile);
        return new JdbcTemplate(dataSource);
    }
}
