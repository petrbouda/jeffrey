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

package cafe.jeffrey.profile.manager.model.io;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.List;

/**
 * Groups file I/O events by their parent directory (the path up to the last {@code /}), accumulating
 * op count, bytes and total/max duration, ordered by descending bytes. Surfaces the hot folder
 * (a log directory, a data directory) behind file I/O. Reuses the {@link IoEndpoint} shape with the
 * directory as the target.
 */
public class IoDirectoriesBuilder implements RecordBuilder<GenericRecord, List<IoEndpoint>> {

    private static final String ROOT = "/";

    private final IoEndpointGrouping grouping = new IoEndpointGrouping();

    @Override
    public void onRecord(GenericRecord record) {
        grouping.record(directoryOf(IoEventFields.filePath(record.jsonFields())), record);
    }

    @Override
    public List<IoEndpoint> build() {
        return grouping.rankedByBytes();
    }

    private static String directoryOf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash > 0) {
            return path.substring(0, slash);
        }
        if (slash == 0) {
            return ROOT;
        }
        return path;
    }
}
