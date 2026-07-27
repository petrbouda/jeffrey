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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.jfrparser.api.type.JfrThread;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.ThreadInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the events behind the timeline's bands. Unlike {@link ThreadsRecordBuilder} it reads no
 * JSON fields and no event labels — the timeline draws rectangles, and the fields belong to the
 * tooltip lookup, which queries a single band's worth of events at a time.
 */
public class ThreadTimelineRecordBuilder implements RecordBuilder<GenericRecord, List<ThreadTimelineEvent>> {

    private final List<ThreadTimelineEvent> result = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        JfrThread thread = record.thread();
        result.add(new ThreadTimelineEvent(
                new ThreadInfo(thread.osThreadId(), thread.javaThreadId(), thread.name()),
                record.timestampFromStart(),
                record.duration(),
                ThreadState.fromEventType(record.type())));
    }

    @Override
    public List<ThreadTimelineEvent> build() {
        return result;
    }
}
