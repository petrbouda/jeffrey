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

package pbouda.jeffrey.generator.subsecond.db;

import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.db.RecordBuilder;

import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

public class SubSecondRecordBuilder implements RecordBuilder<StackBasedRecord, SingleResult> {

    private final boolean collectWeight;
    private final List<SecondColumn> columns = new ArrayList<>();
    private long maxvalue = 0;

    public SubSecondRecordBuilder(SubSecondConfig config) {
        this.collectWeight = config.collectWeight();
    }

    @Override
    public void onRecord(StackBasedRecord record) {
        Instant relative = record.timestamp();
        int relativeSeconds = (int) relative.getEpochSecond();
        int millisInSecond = relative.get(ChronoField.MILLI_OF_SECOND);

        // Value for the new second/column arrived, then create a new column for it.
        int expectedColumns = relativeSeconds + 1;
        if (expectedColumns > columns.size()) {
            appendMoreColumns(expectedColumns);
        }

        long value = collectWeight ? record.sampleWeight() : record.samples();

        // Increment a value in the bucket and return a new value to track the
        // `maxvalue` from all buckets and columns.
        long newValue = columns.get(relativeSeconds).increment(millisInSecond, value);
        if (newValue > maxvalue) {
            maxvalue = newValue;
        }
    }

    private void appendMoreColumns(long newSize) {
        long columnsToAdd = newSize - columns.size();
        for (int i = 0; i < columnsToAdd; i++) {
            columns.add(new SecondColumn());
        }
    }

    @Override
    public SingleResult build() {
        return new SingleResult(maxvalue, columns);
    }
}
