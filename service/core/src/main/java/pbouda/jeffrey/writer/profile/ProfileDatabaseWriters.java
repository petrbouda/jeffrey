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

package pbouda.jeffrey.writer.profile;

import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.profile.*;
import pbouda.jeffrey.persistence.profile.factory.JdbcTemplateProfileFactory;

import javax.sql.DataSource;
import java.util.function.Supplier;

public class ProfileDatabaseWriters {

    private final int batchSize;

    public ProfileDatabaseWriters(int batchSize) {
        this.batchSize = batchSize;
    }

    public Supplier<BatchingDatabaseWriter<EventType>> eventTypes(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.writerForEvents(profileDirs);
        return () -> new BatchingEventTypeWriter(dataSource, batchSize);
    }

    public Supplier<BatchingDatabaseWriter<Event>> events(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.writerForEvents(profileDirs);
        return () -> new BatchingEventWriter(dataSource, batchSize);
    }

    public Supplier<BatchingDatabaseWriter<EventStacktrace>> stacktraces(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.writerForEvents(profileDirs);
        return () -> new BatchingStacktraceWriter(dataSource, batchSize);
    }

    public Supplier<BatchingDatabaseWriter<EventStacktraceTag>> stacktraceTags(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.writerForEvents(profileDirs);
        return () -> new BatchingStacktraceTagWriter(dataSource, batchSize);
    }

    public Supplier<BatchingDatabaseWriter<EventThread>> threads(ProfileDirs profileDirs) {
        DataSource dataSource = JdbcTemplateProfileFactory.writerForEvents(profileDirs);
        return () -> new BatchingThreadWriter(dataSource, batchSize);
    }
}
