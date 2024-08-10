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

package pbouda.jeffrey.generator.subsecond.api;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.generator.subsecond.SubSecondConfig;
import pbouda.jeffrey.generator.subsecond.SubSecondEventProcessor;
import pbouda.jeffrey.generator.subsecond.collector.SubSecondCollectorFactory;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

public class SubSecondGeneratorImpl implements SubSecondGenerator {

    @Override
    public JsonNode generate(SubSecondConfig config) {
        return RecordingIterators.automaticAndCollect(
                config.recordings(),
                () -> new SubSecondEventProcessor(config),
                new SubSecondCollectorFactory()
        );
    }
}
