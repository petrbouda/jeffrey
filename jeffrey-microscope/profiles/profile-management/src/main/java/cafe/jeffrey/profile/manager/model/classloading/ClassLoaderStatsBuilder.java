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

package cafe.jeffrey.profile.manager.model.classloading;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses periodic {@code jdk.ClassLoaderStatistics} snapshots into one row per class loader. Each
 * loader is keyed by its native {@code classLoaderData} address; with a time-ordered stream the last
 * snapshot wins, giving the most recent state per loader. The result is ordered by descending
 * metaspace footprint so the heaviest loaders surface first.
 */
public class ClassLoaderStatsBuilder implements RecordBuilder<GenericRecord, List<ClassLoaderStat>> {

    private static final String BOOTSTRAP_LOADER_NAME = "Bootstrap Class Loader";
    private static final String CLASS_LOADER_FIELD = "classLoader";
    private static final String PARENT_CLASS_LOADER_FIELD = "parentClassLoader";
    private static final String CLASS_LOADER_DATA_FIELD = "classLoaderData";
    private static final String CLASS_COUNT_FIELD = "classCount";
    private static final String CHUNK_SIZE_FIELD = "chunkSize";
    private static final String BLOCK_SIZE_FIELD = "blockSize";
    private static final String HIDDEN_CLASS_COUNT_FIELD = "hiddenClassCount";
    private static final String HIDDEN_CHUNK_SIZE_FIELD = "hiddenChunkSize";

    private final Map<Long, ClassLoaderStat> statsByLoaderData = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long loaderData = Json.readLong(fields, CLASS_LOADER_DATA_FIELD);

        ClassLoaderStat stat = new ClassLoaderStat(
                loaderLabel(Json.readString(fields, CLASS_LOADER_FIELD)),
                Json.readString(fields, PARENT_CLASS_LOADER_FIELD),
                Math.max(0, Json.readLong(fields, CLASS_COUNT_FIELD)),
                Math.max(0, Json.readLong(fields, CHUNK_SIZE_FIELD)),
                Math.max(0, Json.readLong(fields, BLOCK_SIZE_FIELD)),
                Math.max(0, Json.readLong(fields, HIDDEN_CLASS_COUNT_FIELD)),
                Math.max(0, Json.readLong(fields, HIDDEN_CHUNK_SIZE_FIELD)));

        statsByLoaderData.put(loaderData, stat);
    }

    private static String loaderLabel(String rawLabel) {
        return rawLabel == null ? BOOTSTRAP_LOADER_NAME : rawLabel;
    }

    @Override
    public List<ClassLoaderStat> build() {
        List<ClassLoaderStat> result = new ArrayList<>(statsByLoaderData.values());
        result.sort(Comparator.comparingLong(ClassLoaderStat::metaspaceBytes).reversed());
        return result;
    }
}
