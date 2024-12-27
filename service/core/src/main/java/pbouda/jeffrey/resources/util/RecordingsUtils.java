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

package pbouda.jeffrey.resources.util;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Recording;
import pbouda.jeffrey.common.treetable.RecordingData;
import pbouda.jeffrey.common.treetable.Tree;
import pbouda.jeffrey.common.treetable.TreeData;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

public abstract class RecordingsUtils {

    public static Set<String> toUiSuggestions(List<Recording> recordings) {
        Set<String> paths = new HashSet<>();

        for (Recording recording : recordings) {
            Path directories = recording.relativePath().getParent();
            if (directories == null) {
                continue;
            }

            Path current = null;
            for (Path part : directories) {
                current = current == null ? part : current.resolve(part);
                paths.add(current.toString());
            }
        }
        return paths;
    }

    public static JsonNode toUiTree(List<Recording> recordings) {
        Tree tree = new Tree();
        for (Recording recording : sortRecordings(recordings)) {
            TreeData data = new RecordingData(
                    generateCategories(recording),
                    recording.relativePath().getFileName().toString(),
                    recording.dateTime().toString(),
                    recording.sizeInBytes());

            tree.add(data);
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    private static List<Recording> sortRecordings(List<Recording> recordings) {
        return recordings.stream()
                .sorted((r1, r2) -> r2.relativePath().getNameCount() - r1.relativePath().getNameCount())
                .toList();
    }

    private static List<String> generateCategories(Recording recording) {
        java.nio.file.Path parent = recording.relativePath().getParent();
        if (parent == null || parent.getNameCount() == 0) {
            return List.of();
        }

        return StreamSupport.stream(parent.spliterator(), false)
                .map(java.nio.file.Path::toString)
                .toList();
    }
}
