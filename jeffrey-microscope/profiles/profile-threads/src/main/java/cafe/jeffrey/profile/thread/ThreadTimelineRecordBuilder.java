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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.jfrparser.api.type.JfrThread;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.microscope.model.ThreadInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the events behind the timeline's bands. Unlike {@link ThreadWindowEventsBuilder} it reads
 * no JSON fields and no event labels — the timeline draws rectangles, and the fields belong to the
 * tooltip lookup, which queries one hovered window's worth of events at a time.
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
