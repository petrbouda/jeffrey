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
