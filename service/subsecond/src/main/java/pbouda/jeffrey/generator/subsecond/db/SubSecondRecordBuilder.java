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

import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.model.SubSecondRecord;

import java.util.ArrayList;
import java.util.List;

public class SubSecondRecordBuilder implements RecordBuilder<SubSecondRecord, SingleResult> {

    private final List<SecondColumn> columns = new ArrayList<>();
    private long maxvalue = 0;

    @Override
    public void onRecord(SubSecondRecord record) {
        long millis = record.timestampFromStart();
        int seconds = (int) millis / 1000;
        int millisInSecond = (int) millis % 1000;

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
            columns.add(new SecondColumn());
        }
    }

    @Override
    public SingleResult build() {
        return new SingleResult(maxvalue, columns);
    }
}
