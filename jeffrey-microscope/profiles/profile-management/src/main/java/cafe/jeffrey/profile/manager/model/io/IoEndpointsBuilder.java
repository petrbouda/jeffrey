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
 * Groups I/O events by endpoint — socket peer ({@code host:port}) or file path — accumulating
 * op count, total bytes and total/max duration, ordered by descending bytes. The caller scopes the
 * event stream to either socket or file events, so the same builder serves both Top Peers and Files.
 */
public class IoEndpointsBuilder implements RecordBuilder<GenericRecord, List<IoEndpoint>> {

    private final IoEndpointGrouping grouping = new IoEndpointGrouping();

    @Override
    public void onRecord(GenericRecord record) {
        grouping.record(IoEventFields.target(record.type(), record.jsonFields()), record);
    }

    @Override
    public List<IoEndpoint> build() {
        return grouping.rankedByBytes();
    }
}
