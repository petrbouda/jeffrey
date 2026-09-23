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
