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

package pbouda.jeffrey.generator.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.marker.Marker;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;

import java.util.List;

public class DiffgraphGeneratorImpl implements GraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        DiffFrame diffFrame = config.eventType().isAllocationEvent()
                ? DifferentialRecordingIterators.allocation(config)
                : DifferentialRecordingIterators.simple(config);

        return new DiffgraphFormatter(diffFrame).format();
    }

    @Override
    public ObjectNode generate(Config config, List<Marker> marker) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
