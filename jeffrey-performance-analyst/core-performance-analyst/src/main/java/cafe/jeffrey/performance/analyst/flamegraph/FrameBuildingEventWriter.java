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

package cafe.jeffrey.performance.analyst.flamegraph;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.shared.common.model.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link EventWriter} that builds in-memory flamegraph {@link Frame} trees. Each parsing thread gets its
 * own {@link FrameBuildingSingleThreadedEventWriter} via {@link #newSingleThreadedWriter()}; once parsing
 * finishes, {@link #onComplete()} deep-merges every per-thread tree into one {@link Frame} per event type.
 */
public class FrameBuildingEventWriter implements EventWriter {

    private final List<FrameBuildingSingleThreadedEventWriter> writers = new CopyOnWriteArrayList<>();

    private volatile Map<Type, Frame> result = Map.of();

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        FrameBuildingSingleThreadedEventWriter writer = new FrameBuildingSingleThreadedEventWriter();
        writers.add(writer);
        return writer;
    }

    @Override
    public void onComplete() {
        Map<Type, Frame> merged = new HashMap<>();
        for (FrameBuildingSingleThreadedEventWriter writer : writers) {
            for (Map.Entry<Type, Frame> entry : writer.result().entrySet()) {
                Frame target = merged.computeIfAbsent(entry.getKey(), t -> Frame.emptyFrame());
                FrameTreeMerger.mergeInto(target, entry.getValue());
            }
        }
        this.result = merged;
    }

    /**
     * The merged trees, one per event type. Empty until {@link #onComplete()} has run.
     */
    public Map<Type, Frame> result() {
        return result;
    }
}
