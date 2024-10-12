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

package pbouda.jeffrey.controller.util;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.treetable.RecordingData;
import pbouda.jeffrey.common.treetable.Tree;
import pbouda.jeffrey.common.treetable.TreeData;
import pbouda.jeffrey.common.Recording;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

public abstract class RecordingUtils {

    public static JsonNode toUiTree(List<Recording> recordings) {
        Tree tree = new Tree();
        for (Recording recording : recordings) {
            TreeData data = new RecordingData(
                    generateCategories(recording),
                    recording.relativePath().getFileName().toString(),
                    recording.dateTime().toString(),
                    recording.sizeInBytes());

            tree.add(data);
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    private static List<String> generateCategories(Recording recording) {
        Path parent = recording.relativePath().getParent();
        if (parent == null || parent.getNameCount() == 0) {
            return List.of();
        }

        return StreamSupport.stream(parent.spliterator(), false)
                .map(Path::toString)
                .toList();
    }
}
