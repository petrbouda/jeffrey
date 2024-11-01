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

package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;

import java.nio.file.Path;

public abstract class JdbcTemplateFactory {

    public static JdbcTemplate create(ProfileDirs profileDirs) {
        return createForDbFile(profileDirs.database());
    }

    public static JdbcTemplate create(ProjectDirs projectDirs) {
        return createForDbFile(projectDirs.database());
    }

    public static JdbcTemplate create(HomeDirs homeDirs) {
        return createForDbFile(homeDirs.database());
    }

    private static JdbcTemplate createForDbFile(Path dbFile) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbFile);
        return new JdbcTemplate(dataSource);
    }
}
