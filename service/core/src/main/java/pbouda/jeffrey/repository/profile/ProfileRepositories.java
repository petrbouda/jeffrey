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

package pbouda.jeffrey.repository.profile;

import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.profile.Event;
import pbouda.jeffrey.common.model.profile.EventStacktrace;
import pbouda.jeffrey.common.model.profile.EventThread;
import pbouda.jeffrey.repository.factory.JdbcTemplateProfileFactory;

import javax.sql.DataSource;
import java.util.function.Supplier;

public class ProfileRepositories {

    private final int batchSize;

    public ProfileRepositories(int batchSize) {
        this.batchSize = batchSize;
    }

    public ProfileRepository profile(ProfileDirs profileDirs) {
        return new ProfileRepository(JdbcTemplateProfileFactory.createCommon(profileDirs));
    }

    public Supplier<BatchingDatabaseWriter<Event>> events(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.createDataSourceForEvents(profileDirs);
        return () -> new BatchingEventWriter(dataSource, batchSize);
    }

    public Supplier<BatchingDatabaseWriter<EventStacktrace>> stacktraces(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.createDataSourceForEvents(profileDirs);
        return () -> new BatchingStacktraceWriter(dataSource, batchSize);
    }

    public Supplier<BatchingDatabaseWriter<EventThread>> threads(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.createDataSourceForEvents(profileDirs);
        return () -> new BatchingThreadWriter(dataSource, batchSize);
    }
}
