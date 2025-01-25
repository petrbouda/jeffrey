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

package pbouda.jeffrey.persistence.profile.factory;

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

    private static final SQLiteConfig WRITER_CONFIG;
    private static final SQLiteConfig READER_CONFIG;

    static {
        WRITER_CONFIG = new SQLiteConfig();
        WRITER_CONFIG.setJournalMode(JournalMode.WAL);
        WRITER_CONFIG.setSynchronous(SynchronousMode.OFF);
        WRITER_CONFIG.setTransactionMode(TransactionMode.DEFERRED);

        READER_CONFIG = new SQLiteConfig();
        READER_CONFIG.setJournalMode(JournalMode.WAL);
    }

    public static JdbcTemplate createCommon(ProfileDirs profileDirs) {
        return createForDbFile(profileDirs.databaseCommon(), null);
    }

    public static JdbcTemplate createEvents(ProfileDirs profileDirs) {
        return createForDbFile(profileDirs.databaseEvents(), null);
    }

    public static JdbcTemplate createEventsReader(ProfileDirs profileDirs) {
        return createForDbFile(profileDirs.databaseEvents(), READER_CONFIG);
    }

    public static DataSource readerForEvents(ProfileDirs profileDirs) {
        SQLiteDataSource dataSource = new SQLiteDataSource(READER_CONFIG);
        dataSource.setUrl("jdbc:sqlite:" + profileDirs.databaseEvents());
        return dataSource;
    }

    public static DataSource writerForEvents(ProfileDirs profileDirs) {
        SQLiteDataSource dataSource = new SQLiteDataSource(WRITER_CONFIG);
        dataSource.setUrl("jdbc:sqlite:" + profileDirs.databaseEvents());
        return dataSource;
    }

    private static JdbcTemplate createForDbFile(Path dbFile, SQLiteConfig config) {
        SQLiteDataSource dataSource;
        if (config != null) {
            dataSource = new SQLiteDataSource(config);
        } else {
            dataSource = new SQLiteDataSource();
        }

        dataSource.setUrl("jdbc:sqlite:" + dbFile);
        return new JdbcTemplate(dataSource);
    }
}
