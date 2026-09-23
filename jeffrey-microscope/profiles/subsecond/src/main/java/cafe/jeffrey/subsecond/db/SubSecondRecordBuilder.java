/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.subsecond.db;

import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.SubSecondRecord;

import java.util.ArrayList;
import java.util.List;

public class SubSecondRecordBuilder implements RecordBuilder<SubSecondRecord, SingleResult> {

    private final List<SecondColumn> columns = new ArrayList<>();
    private final long timeRangeStartMillis;
    private final int bucketSizeMs;
    private long maxvalue = 0;

    public SubSecondRecordBuilder(long timeRangeStartMillis) {
        this(timeRangeStartMillis, SecondColumn.BUCKET_SIZE);
    }

    public SubSecondRecordBuilder(long timeRangeStartMillis, int bucketSizeMs) {
        this.timeRangeStartMillis = timeRangeStartMillis;
        this.bucketSizeMs = bucketSizeMs;
    }

    @Override
    public void onRecord(SubSecondRecord record) {
        long millis = record.timestampFromStart() - timeRangeStartMillis;
        int seconds = (int) (millis / 1000);
        int millisInSecond = (int) (millis % 1000);

        // Value for the new second/column arrived, then create a new column for it.
        int expectedColumns = seconds + 1;
        if (expectedColumns > columns.size()) {
            appendMoreColumns(expectedColumns);
        }

        // Increment a value in the bucket and return a new value to track the
        // `maxvalue` from all buckets and columns.
        long newValue = columns.get(seconds).increment(millisInSecond, record.value());
        if (newValue > maxvalue) {
            maxvalue = newValue;
        }
    }

    private void appendMoreColumns(long newSize) {
        long columnsToAdd = newSize - columns.size();
        for (int i = 0; i < columnsToAdd; i++) {
            columns.add(new SecondColumn(bucketSizeMs));
        }
    }

    @Override
    public SingleResult build() {
        return new SingleResult(maxvalue, columns);
    }
}
