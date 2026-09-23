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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;

import java.util.List;

public abstract class SubSecondCollectorUtils {

    private static final JsonNode EMPTY_DATA = Json.mapper()
            .valueToTree(new SubSecondModel(0, Json.createArray()));

    public static JsonNode finisher(SingleResult combined) {
        if (combined.columns().isEmpty()) {
            return EMPTY_DATA;
        }

        // All columns share the same bucket geometry (built by one SubSecondRecordBuilder).
        SecondColumn firstColumn = combined.columns().getFirst();
        int bucketSize = firstColumn.getBucketSize();
        int bucketCount = firstColumn.getBucketCount();

        long[][] matrix = generateMatrix(combined.columns(), bucketCount);

        return Json.mapper()
                .valueToTree(new SubSecondModel(combined.maxValue(), formatMatrix(matrix, bucketSize)));
    }

    private static ArrayNode formatMatrix(long[][] matrix, int bucketSize) {
        ArrayNode output = Json.createArray();

        for (int i = 0; i < matrix.length; i++) {
            ArrayNode cells = Json.createArray();
            for (int j = 0; j < matrix[i].length; j++) {
                ObjectNode cell = Json.createObject();
                cell.put("x", String.valueOf(j + 1));
                cell.put("y", matrix[i][j]);
                cells.add(cell);
            }

            JsonNode row = Json.createObject()
                    .put("name", String.valueOf(i * bucketSize))
                    .set("data", cells);

            output.add(row);
        }

        return output;
    }

    private static long[][] generateMatrix(List<SecondColumn> columns, int bucketCount) {
        long[][] matrix = new long[bucketCount][];
        for (int i = 0; i < bucketCount; i++) {
            long[] row = new long[columns.size()];
            for (int j = 0; j < columns.size(); j++) {
                row[j] = columns.get(j).getBuckets()[i];
            }
            matrix[i] = row;
        }
        return matrix;
    }
}
