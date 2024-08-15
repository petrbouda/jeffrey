/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.generator.subsecond.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.generator.subsecond.SecondColumn;
import pbouda.jeffrey.generator.subsecond.SingleResult;
import pbouda.jeffrey.generator.subsecond.SubSecondModel;

import java.util.List;

public abstract class SubSecondCollectorUtils {

    private static final JsonNode EMPTY_DATA = Json.mapper()
            .valueToTree(new SubSecondModel(0, Json.createArray()));

    public static JsonNode finisher(SingleResult combined, long maxValue) {
        if (combined.columns().isEmpty()) {
            return EMPTY_DATA;
        }

        long[][] matrix = generateMatrix(combined.columns());

        return Json.mapper()
                .valueToTree(new SubSecondModel(maxValue, formatMatrix(matrix)));
    }

    private static ArrayNode formatMatrix(long[][] matrix) {
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
                    .put("name", String.valueOf(i * SecondColumn.BUCKET_SIZE))
                    .set("data", cells);

            output.add(row);
        }

        return output;
    }

    private static long[][] generateMatrix(List<SecondColumn> columns) {
        long[][] matrix = new long[SecondColumn.BUCKET_COUNT][];
        for (int i = 0; i < SecondColumn.BUCKET_COUNT; i++) {
            long[] row = new long[columns.size()];
            for (int j = 0; j < columns.size(); j++) {
                row[j] = columns.get(j).getBuckets()[i];
            }
            matrix[i] = row;
        }
        return matrix;
    }
}
